package io.sonarswift.cli.pipeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Root of a pipeline definition.
 *
 * <p>Schema (YAML at {@code .sonar-swift/pipeline.yml}):</p>
 * <pre>
 * name: ios-default
 * description: Default sonar-swift pipeline
 * env:
 *   XCODE_SCHEME: MyApp
 * stages:
 *   - id: lint
 *     ...
 * </pre>
 *
 * <p>Schema versioning: we don't have a {@code version:} field yet — when the
 * shape changes we'll add one with backward-compat parsing. Today the loader
 * accepts unknown fields silently (see {@code @JsonIgnoreProperties}).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Pipeline {

    /** Display name shown in dashboard + reporter output. */
    public String name = "pipeline";

    /** Optional human description. */
    public String description = "";

    /** Pipeline-wide environment variables; merged into each step's env. */
    public Map<String, String> env = new LinkedHashMap<>();

    /** Stages, declared in the order they should run (deps may reorder). */
    public List<Stage> stages = List.of();

    /**
     * Optional pipeline-level "fail fast" — when a stage errors,
     * abort remaining stages even if {@code continueOnError} is false on a sibling.
     * Default false: dependent stages skip, independent stages still run.
     */
    public boolean failFast = false;
}
