package io.sonarswift.plugin.metrics;

import io.sonarswift.plugin.parser.Token;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;

import java.util.List;
import java.util.Set;

/** Counts classes, functions, statements at the file level. */
public class StructuralMetricsCalculator {

    private static final Set<String> CLASS_LIKE = Set.of(
            "class", "struct", "enum", "actor", "protocol"
    );

    private static final Set<String> FUNCTION_LIKE = Set.of(
            "func", "init"
    );

    public void compute(InputFile file, List<Token> tokens, SensorContext context) {
        int classes = 0;
        int functions = 0;
        int statements = 0;

        for (Token t : tokens) {
            if (t.kind() != Token.Kind.KEYWORD) continue;
            if (CLASS_LIKE.contains(t.text())) classes++;
            else if (FUNCTION_LIKE.contains(t.text())) functions++;
        }

        // Statements: approximated by counting line endings that contain code,
        // minus block braces and a few exclusions. Token-level approximation;
        // AST gives exact.
        int currentLine = -1;
        boolean lineHasCode = false;
        for (Token t : tokens) {
            if (!t.isCode()) continue;
            if (t.line() != currentLine) {
                if (lineHasCode) statements++;
                currentLine = t.line();
                lineHasCode = false;
            }
            // Exclude lone braces / parens
            if (t.kind() == Token.Kind.PUNCT
                    && ("{".equals(t.text()) || "}".equals(t.text()))) continue;
            lineHasCode = true;
        }
        if (lineHasCode) statements++;

        context.<Integer>newMeasure().forMetric(CoreMetrics.CLASSES).on(file)
                .withValue(classes).save();
        context.<Integer>newMeasure().forMetric(CoreMetrics.FUNCTIONS).on(file)
                .withValue(functions).save();
        context.<Integer>newMeasure().forMetric(CoreMetrics.STATEMENTS).on(file)
                .withValue(statements).save();
    }
}
