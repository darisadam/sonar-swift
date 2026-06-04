package io.sonarswift.cli.pipeline.builtin;

import java.util.List;
import java.util.Map;

/**
 * One built-in step type. Maps a step's {@code with:} params to a shell-ready
 * argv that the runner will execute.
 *
 * <p>Why argv (not "a shell command string"): keeps quoting safe by default.
 * If a step needs shell pipes / redirects, it should use the literal
 * {@code run:} form.</p>
 */
public interface BuiltinStep {

    /** Built-in id (the value of the step's {@code uses:} field). */
    String id();

    /**
     * One-line description used in {@code sonar-swift pipeline list --builtins}.
     */
    String description();

    /**
     * Required + optional parameter names. The runner uses these for
     * {@code pipeline validate} diagnostics.
     */
    Params params();

    /** Build the argv to invoke for the given step config. */
    List<String> render(Map<String, Object> with);

    /**
     * Optional pre-flight check — e.g. is `swiftlint` on PATH? Returns null
     * if all good, otherwise an explanatory error to surface to the user.
     */
    default String preflight() { return null; }

    record Params(List<String> required, List<String> optional) {
        public static Params of(List<String> required, List<String> optional) {
            return new Params(required == null ? List.of() : required,
                              optional == null ? List.of() : optional);
        }
    }
}
