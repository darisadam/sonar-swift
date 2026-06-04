package io.sonarswift.cli.pipeline.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Serialiser for {@link RunResult}. Kept separate so the model class stays a
 * pure POJO and the dashboard can pull the JSON without Jackson on its
 * classpath.
 */
public final class RunStateJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private RunStateJson() {}

    public static String write(RunResult result) {
        try {
            return MAPPER.writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
