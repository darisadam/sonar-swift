package io.sonarswift.plugin.profile;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonarsource.analyzer.commons.BuiltInQualityProfileJsonLoader;

public class ObjectiveCQualityProfile implements BuiltInQualityProfilesDefinition {

    private static final String SONAR_WAY_PATH = "/io/sonarswift/plugin/profiles/Sonar_way_profile_objc.json";
    private static final String STRICT_PATH = "/io/sonarswift/plugin/profiles/Strict_profile_objc.json";

    @Override
    public void define(Context context) {
        defineProfile(context, SwiftPluginConstants.PROFILE_SONAR_WAY, SONAR_WAY_PATH, true);
        defineProfile(context, SwiftPluginConstants.PROFILE_STRICT, STRICT_PATH, false);
    }

    private void defineProfile(Context context, String name, String resourcePath, boolean isDefault) {
        NewBuiltInQualityProfile profile = context.createBuiltInQualityProfile(
                name, SwiftPluginConstants.OBJC_LANGUAGE_KEY);
        BuiltInQualityProfileJsonLoader.load(
                profile,
                SwiftPluginConstants.OBJC_REPOSITORY_KEY,
                resourcePath);
        profile.setDefault(isDefault);
        profile.done();
    }
}
