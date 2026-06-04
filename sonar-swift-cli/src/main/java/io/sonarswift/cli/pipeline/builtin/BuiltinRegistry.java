package io.sonarswift.cli.pipeline.builtin;

import io.sonarswift.cli.pipeline.builtin.steps.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lookup of built-in steps by {@code uses:} key. */
public final class BuiltinRegistry {

    private static final Map<String, BuiltinStep> STEPS = new LinkedHashMap<>();

    static {
        register(new SwiftLintStep());
        register(new SwiftFormatStep());
        register(new XcodeBuildStep());
        register(new XcodeTestStep());
        register(new XccovStep());
        register(new SlatherStep());
        register(new GitleaksStep());
        register(new TrufflehogStep());
        register(new SemgrepStep());
        register(new TrivyFsStep());
        register(new DependencyCheckStep());
        register(new SonarScannerStep());
        register(new SonarSwiftQuickStep());
        register(new CommandStep());
        register(new ScriptStep());
    }

    private BuiltinRegistry() {}

    private static void register(BuiltinStep step) {
        STEPS.put(step.id(), step);
    }

    public static BuiltinStep lookup(String id) {
        return STEPS.get(id);
    }

    public static List<BuiltinStep> all() {
        return List.copyOf(STEPS.values());
    }
}
