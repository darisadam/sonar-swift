package io.sonarswift.cli.pipeline;

import io.sonarswift.cli.pipeline.builtin.BuiltinRegistry;
import io.sonarswift.cli.pipeline.builtin.BuiltinStep;
import io.sonarswift.cli.pipeline.model.Pipeline;
import io.sonarswift.cli.pipeline.model.Stage;
import io.sonarswift.cli.pipeline.model.Step;
import io.sonarswift.cli.pipeline.run.PipelineRunner;
import io.sonarswift.cli.pipeline.run.Reporter;
import io.sonarswift.cli.pipeline.run.RunResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Top-level dispatcher for the {@code sonar-swift pipeline} subcommand.
 *
 * <pre>
 * sonar-swift pipeline init   [--template ios-app|swift-package|minimal] [--force]
 * sonar-swift pipeline run    [--file PATH] [--stage ID] [--from ID] [--list]
 * sonar-swift pipeline validate [--file PATH]
 * sonar-swift pipeline list   [--builtins]
 * </pre>
 */
public final class PipelineCommand {

    private PipelineCommand() {}

    public static int dispatch(String[] args) throws IOException {
        if (args.length == 0) {
            printHelp();
            return 0;
        }
        String sub = args[0];
        String[] tail = new String[args.length - 1];
        System.arraycopy(args, 1, tail, 0, tail.length);

        return switch (sub) {
            case "init" -> init(tail);
            case "run" -> run(tail);
            case "validate" -> validate(tail);
            case "list" -> list(tail);
            case "help", "-h", "--help" -> { printHelp(); yield 0; }
            default -> {
                System.err.println("Unknown pipeline subcommand: " + sub);
                printHelp();
                yield 2;
            }
        };
    }

    // ---- init ----

    private static int init(String[] args) throws IOException {
        String template = "ios-app";
        boolean force = false;
        Path target = Path.of(".sonar-swift", "pipeline.yml");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--template" -> template = args[++i];
                case "--force" -> force = true;
                case "--file" -> target = Path.of(args[++i]);
                default -> { System.err.println("Unknown flag: " + args[i]); return 2; }
            }
        }

        String resource = "pipeline/templates/" + template + ".yml";
        try (InputStream is = PipelineCommand.class.getClassLoader().getResourceAsStream(resource)) {
            if (is == null) {
                System.err.println("Unknown template `" + template
                        + "`. Available: ios-app, swift-package, minimal");
                return 2;
            }
            Files.createDirectories(target.getParent() == null ? Path.of(".") : target.getParent());
            if (Files.exists(target) && !force) {
                System.err.println(target + " already exists. Pass --force to overwrite.");
                return 1;
            }
            Files.write(target, is.readAllBytes(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            System.out.println("Wrote " + target);
            System.out.println("Next: edit the file, then `sonar-swift pipeline run`.");
            return 0;
        }
    }

    // ---- run ----

    private static int run(String[] args) throws IOException {
        Path file = null;
        Path projectRoot = Path.of(".").toAbsolutePath().normalize();
        String only = null;
        String from = null;
        boolean listOnly = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file" -> file = Path.of(args[++i]);
                case "--root" -> projectRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--stage" -> only = args[++i];
                case "--from" -> from = args[++i];
                case "--list" -> listOnly = true;
                default -> { System.err.println("Unknown flag: " + args[i]); return 2; }
            }
        }
        if (file == null) {
            file = PipelineLoader.findDefault(projectRoot);
            if (file == null) {
                System.err.println(
                        "No pipeline.yml found. Run `sonar-swift pipeline init` to create one.");
                return 2;
            }
        }

        Pipeline pipeline = PipelineLoader.load(file);

        if (listOnly) {
            System.out.println("Pipeline: " + pipeline.name);
            for (Stage s : pipeline.stages) {
                System.out.printf("  %-20s %s%s%n", s.id, s.displayName(),
                        s.needs.isEmpty() ? "" : "  (needs: " + String.join(",", s.needs) + ")");
            }
            return 0;
        }

        // --stage / --from filtering
        if (only != null) {
            final String onlyId = only;
            pipeline.stages = pipeline.stages.stream()
                    .filter(s -> s.id.equals(onlyId))
                    .toList();
            if (pipeline.stages.isEmpty()) {
                System.err.println("No stage with id `" + onlyId + "`");
                return 2;
            }
            // Clear dependencies for the filtered set
            for (Stage s : pipeline.stages) s.needs = List.of();
        } else if (from != null) {
            final String fromId = from;
            List<Stage> filtered = new ArrayList<>();
            boolean seen = false;
            for (Stage s : pipeline.stages) {
                if (s.id.equals(fromId)) seen = true;
                if (seen) filtered.add(s);
            }
            if (filtered.isEmpty()) {
                System.err.println("No stage with id `" + fromId + "`");
                return 2;
            }
            pipeline.stages = filtered;
        }

        Reporter reporter = Reporter.defaultReporter();
        PipelineRunner runner = new PipelineRunner(pipeline, projectRoot, reporter);
        RunResult result = runner.run();
        return result.status == RunResult.Status.PASS ? 0 : 1;
    }

    // ---- validate ----

    private static int validate(String[] args) throws IOException {
        Path file = null;
        Path projectRoot = Path.of(".").toAbsolutePath().normalize();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--file" -> file = Path.of(args[++i]);
                case "--root" -> projectRoot = Path.of(args[++i]).toAbsolutePath().normalize();
                default -> { System.err.println("Unknown flag: " + args[i]); return 2; }
            }
        }
        if (file == null) {
            file = PipelineLoader.findDefault(projectRoot);
            if (file == null) {
                System.err.println("No pipeline.yml found");
                return 2;
            }
        }
        try {
            Pipeline p = PipelineLoader.load(file);
            System.out.println("✓ " + file + " — pipeline `" + p.name + "` is valid ("
                    + p.stages.size() + " stages)");
            return 0;
        } catch (IOException e) {
            System.err.println("✗ " + file + " — " + e.getMessage());
            return 1;
        }
    }

    // ---- list ----

    private static int list(String[] args) throws IOException {
        boolean builtins = false;
        for (String a : args) {
            if ("--builtins".equals(a)) builtins = true;
            else { System.err.println("Unknown flag: " + a); return 2; }
        }
        if (builtins) {
            System.out.println("Built-in pipeline steps:");
            for (BuiltinStep s : BuiltinRegistry.all()) {
                System.out.printf("  %-22s %s%n", s.id(), s.description());
                BuiltinStep.Params p = s.params();
                if (!p.required().isEmpty()) {
                    System.out.println("    required: " + String.join(", ", p.required()));
                }
                if (!p.optional().isEmpty()) {
                    System.out.println("    optional: " + String.join(", ", p.optional()));
                }
            }
            return 0;
        }
        // Default: list stages of the default pipeline
        Path file = PipelineLoader.findDefault(Path.of(".").toAbsolutePath());
        if (file == null) {
            System.out.println("No pipeline.yml — try `sonar-swift pipeline list --builtins`");
            return 0;
        }
        Pipeline p = PipelineLoader.load(file);
        System.out.println("Pipeline: " + p.name);
        for (Stage s : p.stages) {
            System.out.println("  - " + s.id + " — " + s.displayName());
            for (Step step : s.steps) {
                System.out.println("      • " + step.displayName()
                        + (step.uses.isBlank() ? "" : "  (" + step.uses + ")"));
            }
        }
        return 0;
    }

    private static void printHelp() {
        System.out.println("""
                sonar-swift pipeline — author, validate, and run CI/CD pipelines locally.

                Usage:
                  sonar-swift pipeline init [--template ios-app|swift-package|minimal] [--force]
                  sonar-swift pipeline run [--file PATH] [--stage ID] [--from ID] [--list]
                  sonar-swift pipeline validate [--file PATH]
                  sonar-swift pipeline list [--builtins]

                Templates:
                  ios-app          full iOS app pipeline (build, unit + UI + integration tests,
                                   secret scans, vulnerability scans, lint, SonarQube)
                  swift-package    Swift Package Manager library pipeline
                  minimal          three-stage example (lint → test → sonar)

                See docs/pipeline.md for the schema and the catalogue of built-in steps.
                """);
    }
}
