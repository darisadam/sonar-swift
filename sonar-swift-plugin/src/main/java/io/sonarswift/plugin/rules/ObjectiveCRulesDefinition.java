package io.sonarswift.plugin.rules;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.SonarRuntime;
import org.sonar.api.server.rule.RulesDefinition;
import org.sonarsource.analyzer.commons.RuleMetadataLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ObjectiveCRulesDefinition implements RulesDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(ObjectiveCRulesDefinition.class);

    static final String RESOURCE_FOLDER = "io/sonarswift/plugin/rules/objc";

    private final SonarRuntime runtime;

    public ObjectiveCRulesDefinition(SonarRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    public void define(Context context) {
        NewRepository repo = context.createRepository(
                        SwiftPluginConstants.OBJC_REPOSITORY_KEY,
                        SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .setName(SwiftPluginConstants.OBJC_REPOSITORY_NAME);

        RuleMetadataLoader loader = new RuleMetadataLoader(RESOURCE_FOLDER, runtime);
        loader.addRulesByRuleKey(repo, ObjectiveCCheckList.KEYS);

        repo.done();
        registerExternalRepository(context, "external_oclint", "OCLint");

        LOG.info("Registered {} Objective-C rules + OCLint external repository",
                ObjectiveCCheckList.KEYS.size());
    }

    private void registerExternalRepository(Context context, String key, String name) {
        NewRepository repo = context.createExternalRepository(
                        key,
                        SwiftPluginConstants.OBJC_LANGUAGE_KEY)
                .setName(name);
        repo.done();
    }
}
