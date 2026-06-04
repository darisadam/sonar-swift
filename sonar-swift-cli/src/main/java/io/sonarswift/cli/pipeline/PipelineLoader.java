package io.sonarswift.cli.pipeline;

import io.sonarswift.cli.pipeline.model.Pipeline;
import io.sonarswift.cli.pipeline.model.Stage;
import io.sonarswift.cli.pipeline.model.Step;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a YAML pipeline definition from disk and runs schema-level validation.
 *
 * <p>Validation today is intentionally light — duplicate ids, missing required
 * fields, unknown step dependencies. We don't run a full JSON Schema, the
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} on the model lets users
 * add fields the runner doesn't yet understand without breaking the build.</p>
 */
public final class PipelineLoader {

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

    private PipelineLoader() {}

    /** Default discovery order. */
    public static List<Path> defaultLocations(Path projectRoot) {
        return List.of(
                projectRoot.resolve(".sonar-swift/pipeline.yml"),
                projectRoot.resolve(".sonar-swift/pipeline.yaml"),
                projectRoot.resolve("sonar-swift.pipeline.yml"));
    }

    public static Path findDefault(Path projectRoot) {
        for (Path candidate : defaultLocations(projectRoot)) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        return null;
    }

    public static Pipeline load(Path path) throws IOException {
        Pipeline pipeline = YAML.readValue(path.toFile(), Pipeline.class);
        List<String> errors = validate(pipeline);
        if (!errors.isEmpty()) {
            throw new IOException("Invalid pipeline " + path + ":\n  - "
                    + String.join("\n  - ", errors));
        }
        return pipeline;
    }

    /** Convenience for tests + dashboard preview. */
    public static Pipeline parseYaml(String yamlText) throws IOException {
        Pipeline pipeline = YAML.readValue(yamlText, Pipeline.class);
        List<String> errors = validate(pipeline);
        if (!errors.isEmpty()) {
            throw new IOException("Invalid pipeline:\n  - " + String.join("\n  - ", errors));
        }
        return pipeline;
    }

    public static List<String> validate(Pipeline p) {
        List<String> errors = new ArrayList<>();
        if (p.stages == null || p.stages.isEmpty()) {
            errors.add("`stages` must contain at least one stage");
            return errors;
        }
        Set<String> stageIds = new HashSet<>();
        for (Stage s : p.stages) {
            if (s.id == null || s.id.isBlank()) {
                errors.add("each stage must declare a non-empty `id`");
                continue;
            }
            if (!stageIds.add(s.id)) {
                errors.add("duplicate stage id: " + s.id);
            }
            if (s.steps == null || s.steps.isEmpty()) {
                errors.add("stage `" + s.id + "` has no `steps`");
                continue;
            }
            Set<String> stepIds = new HashSet<>();
            for (Step step : s.steps) {
                boolean hasUses = step.uses != null && !step.uses.isBlank();
                boolean hasRun = step.run != null && !step.run.isBlank();
                if (hasUses == hasRun) {
                    errors.add("stage `" + s.id + "` step `" + step.displayName()
                            + "` must set exactly one of `uses:` or `run:`");
                }
                if (step.id != null && !step.id.isBlank()) {
                    // Explicit id — duplicates are a user error.
                    if (!stepIds.add(step.id)) {
                        errors.add("stage `" + s.id + "` has duplicate step id: " + step.id);
                    }
                } else {
                    // Auto-generate a unique id so users can write multiple
                    // `uses: command` steps in the same stage without having to
                    // assign ids by hand.
                    String base = defaultStepId(step);
                    String candidate = base;
                    int n = 2;
                    while (!stepIds.add(candidate)) {
                        candidate = base + "-" + n++;
                    }
                    step.id = candidate;
                }
                if (step.timeout <= 0) {
                    step.timeout = 1800;
                }
            }
        }
        // Resolve `needs:` references
        for (Stage s : p.stages) {
            for (String dep : s.needs) {
                if (!stageIds.contains(dep)) {
                    errors.add("stage `" + s.id + "` needs unknown stage `" + dep + "`");
                }
                if (dep.equals(s.id)) {
                    errors.add("stage `" + s.id + "` depends on itself");
                }
            }
        }
        return errors;
    }

    private static String defaultStepId(Step step) {
        if (step.uses != null && !step.uses.isBlank()) return step.uses;
        if (step.run != null && !step.run.isBlank()) {
            String first = step.run.split("\\s+")[0];
            return first.replaceAll("[^A-Za-z0-9_-]", "-");
        }
        return "step";
    }
}
