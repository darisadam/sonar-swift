package io.sonarswift.plugin.rules;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.SonarRuntime;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers Swift rules with SonarQube. Rule metadata is loaded from
 * {@code resources/io/sonarswift/plugin/rules/swift/SXXXX.json} (machine-
 * readable description: key, name, type, severity, tags, SQALE) and the
 * matching {@code SXXXX.html} (UI description shown in the rule browser).
 *
 * <p>To add a new rule:</p>
 * <ol>
 *   <li>Drop a {@code .json} + {@code .html} pair in {@code rules/swift/}</li>
 *   <li>Add the key to {@link SwiftCheckList#KEYS}</li>
 *   <li>Implement the check class extending {@code SwiftCheck}</li>
 * </ol>
 *
 * <p>The agent {@code sonar-rule-author} and the skill {@code add-rule}
 * automate steps 1-3.</p>
 */
public class SwiftRulesDefinition implements RulesDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SwiftRulesDefinition.class);

    static final String RESOURCE_FOLDER = "io/sonarswift/plugin/rules/swift";

    private final SonarRuntime runtime;

    public SwiftRulesDefinition(SonarRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void define(Context context) {
        NewRepository repo = context.createRepository(
                        SwiftPluginConstants.SWIFT_REPOSITORY_KEY,
                        SwiftPluginConstants.SWIFT_LANGUAGE_KEY)
                .setName(SwiftPluginConstants.SWIFT_REPOSITORY_NAME);

        RuleMetadataLoader loader = new RuleMetadataLoader(RESOURCE_FOLDER, runtime);
        loader.addRulesByRuleKey(repo, SwiftCheckList.KEYS);

        // External rule repositories — referenced when SwiftLint findings are imported.
        // Each external tool gets its own repo so users can disable per-tool.
        repo.done();
        registerExternalRepository(context, "external_swiftlint", "SwiftLint");
        registerExternalRepository(context, "external_periphery", "Periphery");
        registerExternalRepository(context, "external_tailor", "Tailor");

        LOG.info("Registered {} Swift rules + 3 external rule repositories",
                SwiftCheckList.KEYS.size());
    }

    private void registerExternalRepository(Context context, String key, String name) {
        NewRepository repo = context.createExternalRepository(
                        key,
                        SwiftPluginConstants.SWIFT_LANGUAGE_KEY)
                .setName(name);
        // External repositories don't define rules up front — each issue
        // imported via a sensor creates an ad-hoc rule for that finding.
        repo.done();
    }
}
