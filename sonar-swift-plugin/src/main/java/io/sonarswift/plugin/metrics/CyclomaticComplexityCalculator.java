package io.sonarswift.plugin.metrics;

import io.sonarswift.plugin.parser.Token;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;

import java.util.List;
import java.util.Set;

/**
 * Cyclomatic complexity — file-level sum.
 *
 * <p>Increments on: {@code if}, {@code for}, {@code while}, {@code repeat},
 * {@code case}, {@code catch}, {@code guard}, {@code ?:}, {@code &&}, {@code ||}.
 * {@code else} (no condition) and {@code default} (in switch) do not increment.</p>
 */
public class CyclomaticComplexityCalculator {

    private static final Set<String> COMPLEXITY_KEYWORDS = Set.of(
            "if", "for", "while", "repeat", "case", "catch", "guard"
    );

    public void compute(InputFile file, List<Token> tokens, SensorContext context) {
        int complexity = 0;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);

            // Each function body baseline starts at 1
            if (t.kind() == Token.Kind.KEYWORD
                    && ("func".equals(t.text()) || "init".equals(t.text()))) {
                complexity++;
                continue;
            }

            if (t.kind() == Token.Kind.KEYWORD && COMPLEXITY_KEYWORDS.contains(t.text())) {
                complexity++;
                continue;
            }

            if (t.kind() == Token.Kind.OPERATOR) {
                if ("&&".equals(t.text()) || "||".equals(t.text()) || "?".equals(t.text())) {
                    // ? is for ternaries — risk of overcounting with optional chaining.
                    // Avoid counting `x?.foo` by checking the next token isn't `.`
                    if ("?".equals(t.text()) && i + 1 < tokens.size()) {
                        Token nxt = tokens.get(i + 1);
                        if (nxt.kind() == Token.Kind.OPERATOR && ".".equals(nxt.text())) continue;
                        if (nxt.kind() == Token.Kind.PUNCT) continue;
                    }
                    complexity++;
                }
            }
        }

        context.<Integer>newMeasure().forMetric(CoreMetrics.COMPLEXITY).on(file)
                .withValue(complexity).save();
    }
}
