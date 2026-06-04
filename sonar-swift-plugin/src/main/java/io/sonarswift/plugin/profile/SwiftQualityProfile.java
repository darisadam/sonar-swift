package io.sonarswift.plugin.profile;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.server.profile.BuiltInQualityProfilesDefinition;
import org.sonarsource.analyzer.commons.BuiltInQualityProfileJsonLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwiftQualityProfile implements BuiltInQualityProfilesDefinition {

    private static final Logger LOG = LoggerFactory.getLogger(SwiftQualityProfile.class);

    private static final String SONAR_WAY_PATH = "/io/sonarswift/plugin/profiles/Sonar_way_profile_swift.json";
    private static final String STRICT_PATH = "/io/sonarswift/plugin/profiles/Strict_profile_swift.json";
    private static final String SWIFTLINT_COMPAT_PATH = "/io/sonarswift/plugin/profiles/SwiftLint_compat_profile_swift.json";

    @Override
    public void define(Context context) {
        defineProfile(context, SwiftPluginConstants.PROFILE_SONAR_WAY, SONAR_WAY_PATH, true);
        defineProfile(context, SwiftPluginConstants.PROFILE_STRICT, STRICT_PATH, false);
        defineProfile(context, SwiftPluginConstants.PROFILE_SWIFTLINT_COMPAT, SWIFTLINT_COMPAT_PATH, false);
    }

    private void defineProfile(Context context, String name, String resourcePath, boolean isDefault) {
        NewBuiltInQualityProfile profile = context.createBuiltInQualityProfile(
                name, SwiftPluginConstants.SWIFT_LANGUAGE_KEY);
        BuiltInQualityProfileJsonLoader.load(
                profile,
                SwiftPluginConstants.SWIFT_REPOSITORY_KEY,
                resourcePath);
        profile.setDefault(isDefault);
        profile.done();
        LOG.debug("Built-in Swift profile '{}' registered (default={})", name, isDefault);
    }
}
