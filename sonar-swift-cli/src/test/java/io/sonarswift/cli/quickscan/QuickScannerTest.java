package io.sonarswift.cli.quickscan;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuickScannerTest {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private PrintStream originalOut;

    @BeforeEach
    void captureStdout() {
        originalOut = System.out;
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void restoreStdout() {
        System.setOut(originalOut);
    }

    @Test
    void flags_force_unwrap_force_try_force_cast_as_blockers(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("Foo.swift");
        Files.writeString(file, """
                func a() {
                    let x = optional!
                    let y = try! foo()
                    let z = obj as! Bar
                }
                """);
        int blockers = QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        assertThat(blockers).isEqualTo(3);
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[S1001]", "[S1004]", "[S1003]");
    }

    @Test
    void flags_hardcoded_secret_and_http_url(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("Secrets.swift");
        Files.writeString(file, """
                let apiKey = "sk_live_abcdef1234567890abcdef"
                let url = "http://example.com/login"
                """);
        int blockers = QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        assertThat(blockers).isGreaterThanOrEqualTo(1); // S1800 is BLOCKER
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[S1800]", "[S1802]");
    }

    @Test
    void flags_swift_concurrency_patterns(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("Concurrency.swift");
        Files.writeString(file, """
                enum FeatureFlags {
                    static var debugMode = false
                }
                func go() {
                    Task.detached { await heavyWork() }
                }
                """);
        QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[S1907]", "[S1905]");
    }

    @Test
    void does_not_flag_force_unwrap_in_test_files(@TempDir Path tmp) throws IOException {
        Path dir = tmp.resolve("MyAppTests");
        Files.createDirectories(dir);
        Path file = dir.resolve("LoginTests.swift");
        Files.writeString(file, """
                func test_login() {
                    let user = currentUser!
                }
                """);
        QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("[S1001]");
    }

    @Test
    void todo_comment_is_a_note_not_a_blocker(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("WithTodo.swift");
        Files.writeString(file, """
                // TODO: refactor this
                func a() {}
                """);
        int blockers = QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        assertThat(blockers).isZero();
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[S1007]");
        assertThat(output).contains("note:");
    }

    @Test
    void emits_json_when_requested(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("Foo.swift");
        Files.writeString(file, "let x = a!\n");
        QuickScanner.scan(List.of(file.toString()), opts("json", false));
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output)
                .startsWith("{")
                .contains("\"rule\":\"S1001\"")
                .contains("\"severity\":\"BLOCKER\"")
                .contains("\"line\":1");
    }

    @Test
    void emits_no_findings_for_clean_file(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("Clean.swift");
        Files.writeString(file, """
                func add(_ a: Int, _ b: Int) -> Int {
                    return a + b
                }
                """);
        int blockers = QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        assertThat(blockers).isZero();
        assertThat(out.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void scans_directories_recursively(@TempDir Path tmp) throws IOException {
        Files.createDirectories(tmp.resolve("Sources/Inner"));
        Files.writeString(tmp.resolve("Sources/A.swift"), "let a = b!\n");
        Files.writeString(tmp.resolve("Sources/Inner/B.swift"), "let c = try! d()\n");
        int blockers = QuickScanner.scan(List.of(tmp.toString()), opts("compiler", false));
        assertThat(blockers).isEqualTo(2);
    }

    @Test
    void does_not_misfire_inside_string_literals(@TempDir Path tmp) throws IOException {
        Path file = tmp.resolve("Strings.swift");
        // The `!` here is inside a string literal — must NOT be flagged as force-unwrap.
        Files.writeString(file, """
                let warning = "use ! sparingly"
                """);
        QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).doesNotContain("[S1001]");
    }

    @Test
    void still_detects_secrets_inside_string_literals(@TempDir Path tmp) throws IOException {
        // Inverse of the previous test: S1800/S1802 are MEANT to look inside strings.
        Path file = tmp.resolve("Bad.swift");
        Files.writeString(file, """
                let token = "sk_live_abcdef1234567890abcd"
                """);
        QuickScanner.scan(List.of(file.toString()), opts("compiler", false));
        String output = out.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("[S1800]");
    }

    private QuickScanner.Options opts(String format, boolean allChecks) {
        QuickScanner.Options o = new QuickScanner.Options();
        o.format = format;
        o.allChecks = allChecks;
        return o;
    }
}
