package io.sonarswift.plugin.language;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.config.Configuration;
import org.sonar.api.resources.AbstractLanguage;

import java.util.Arrays;

public final class Swift extends AbstractLanguage {

    private final Configuration config;

    public Swift(Configuration config) {
        super(SwiftPluginConstants.SWIFT_LANGUAGE_KEY, SwiftPluginConstants.SWIFT_LANGUAGE_NAME);
        this.config = config;
    }

    @Override
    public String[] getFileSuffixes() {
        String[] configured = config.getStringArray(SwiftPluginConstants.PROP_SWIFT_FILE_SUFFIXES);
        if (configured == null || configured.length == 0) {
            return SwiftPluginConstants.SWIFT_FILE_SUFFIXES_DEFAULT.clone();
        }
        return Arrays.stream(configured)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith(".") ? s : "." + s)
                .toArray(String[]::new);
    }
}
