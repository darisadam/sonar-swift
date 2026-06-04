package io.sonarswift.cli.pipeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One step inside a stage. Either references a built-in via {@link #uses}
 * (with structured params in {@link #with}) or runs a literal shell command
 * via {@link #run}. Exactly one of the two should be set.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Step {

    /** Unique-within-stage id. Auto-generated from {@link #name} or {@link #uses} if blank. */
    public String id = "";

    /** Human-readable name. */
    public String name = "";

    /**
     * Built-in step type (e.g. {@code swiftlint}, {@code xcode-test}, {@code gitleaks}).
     * See {@code io.sonarswift.cli.pipeline.builtin} for the registry.
     */
    public String uses = "";

    /** Parameters for the built-in named in {@link #uses}. */
    public Map<String, Object> with = new LinkedHashMap<>();

    /** Literal shell command. Mutually exclusive with {@link #uses}. */
    public String run = "";

    /** Step-scoped env, merged on top of stage env. */
    public Map<String, String> env = new LinkedHashMap<>();

    /** Working directory (relative to project root). Default: project root. */
    public String workingDirectory = "";

    /** Hard timeout in seconds. Default 1800 (30 minutes). */
    public int timeout = 1800;

    /** Skip-expression. Same DSL as {@link Stage#when}. */
    public String when = "";

    /** Treat non-zero exit as a warning, not a failure. */
    public boolean continueOnError = false;

    public String displayName() {
        if (name != null && !name.isBlank()) return name;
        if (id != null && !id.isBlank()) return id;
        if (uses != null && !uses.isBlank()) return uses;
        if (run != null && !run.isBlank()) return run.split("\\s+")[0];
        return "(unnamed)";
    }
}
