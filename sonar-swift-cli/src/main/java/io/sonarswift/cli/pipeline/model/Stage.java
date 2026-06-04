package io.sonarswift.cli.pipeline.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One stage of the pipeline — a named group of related steps. */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Stage {

    /** Unique-within-pipeline id. Referenced by {@link #needs}. */
    public String id = "";

    /** Human-readable name shown to the operator. Falls back to {@link #id}. */
    public String name = "";

    /** Stage ids this stage waits for. Empty = no deps. */
    public List<String> needs = List.of();

    /**
     * Conditional skip — an expression like {@code ${{ env.RUN_UI_TESTS != 'false' }}}.
     * Supports a small DSL — see {@link io.sonarswift.cli.pipeline.expr.Expression}.
     */
    public String when = "";

    /** If true, the stage's steps run concurrently. Default sequential. */
    public boolean parallel = false;

    /**
     * If true, a failure in this stage does NOT fail the pipeline. The dashboard
     * still marks the stage red.
     */
    public boolean continueOnError = false;

    /** Stage-scoped env, merged on top of pipeline env. */
    public Map<String, String> env = new LinkedHashMap<>();

    /** The steps. At least one is required. */
    public List<Step> steps = List.of();

    public String displayName() {
        return name == null || name.isBlank() ? id : name;
    }
}
