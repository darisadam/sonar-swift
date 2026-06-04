package io.sonarswift.plugin.language;

import io.sonarswift.plugin.SwiftPluginConstants;

import org.sonar.api.config.Configuration;
import org.sonar.api.resources.AbstractLanguage;

import java.util.Arrays;

public final class ObjectiveC extends AbstractLanguage {

    private final Configuration config;

    public ObjectiveC(Configuration config) {
        super(SwiftPluginConstants.OBJC_LANGUAGE_KEY, SwiftPluginConstants.OBJC_LANGUAGE_NAME);
        this.config = config;
    }

    @Override
    public String[] getFileSuffixes() {
        String[] configured = config.getStringArray(SwiftPluginConstants.PROP_OBJC_FILE_SUFFIXES);
        if (configured == null || configured.length == 0) {
            return SwiftPluginConstants.OBJC_FILE_SUFFIXES_DEFAULT.clone();
        }
        return Arrays.stream(configured)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.startsWith(".") ? s : "." + s)
                .toArray(String[]::new);
    }
}
