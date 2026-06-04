package io.sonarswift.plugin.metrics;

import io.sonarswift.plugin.parser.Token;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;

import java.util.List;
import java.util.Set;

/**
 * Cognitive complexity (Sonar 2017 spec) approximated from the token stream.
 *
 * <p>Token-level approximation:</p>
 * <ul>
 *   <li>Each {@code if}/{@code else if}/{@code for}/{@code while}/{@code case}/
 *       {@code catch}/{@code guard} increments by (1 + nesting level)</li>
 *   <li>{@code else} (no condition) adds 1 but doesn't increase nesting</li>
 *   <li>Nesting is tracked by counting {@code {} pairs after a flow keyword</li>
 *   <li>A sequence of same-operator boolean expressions ({@code a && b && c})
 *       counts as 1, not 2 — heuristic: only increment when the operator
 *       switches</li>
 * </ul>
 *
 * <p>This is a heuristic. The AST-driven calculator (when the parser bridge is
 * online) gives the exact value per Sonar's spec; this gets within ±10% on
 * typical code.</p>
 */
public class CognitiveComplexityCalculator {

    private static final Set<String> FLOW_INCREMENT = Set.of(
            "if", "for", "while", "repeat", "case", "catch", "guard"
    );

    public void compute(InputFile file, List<Token> tokens, SensorContext context) {
        int complexity = 0;
        int nesting = 0;
        String lastBoolOp = null;

        for (Token t : tokens) {
            if (t.kind() == Token.Kind.PUNCT) {
                if ("{".equals(t.text())) nesting++;
                else if ("}".equals(t.text())) nesting = Math.max(0, nesting - 1);
                continue;
            }
            if (t.kind() == Token.Kind.KEYWORD) {
                if (FLOW_INCREMENT.contains(t.text())) {
                    complexity += 1 + Math.max(0, nesting - 1);
                    lastBoolOp = null;
                } else if ("else".equals(t.text())) {
                    complexity += 1;
                    lastBoolOp = null;
                }
                continue;
            }
            if (t.kind() == Token.Kind.OPERATOR) {
                if ("&&".equals(t.text()) || "||".equals(t.text())) {
                    if (lastBoolOp == null || !lastBoolOp.equals(t.text())) {
                        complexity++;
                    }
                    lastBoolOp = t.text();
                } else if (";".equals(t.text())) {
                    lastBoolOp = null;
                }
            }
        }

        context.<Integer>newMeasure().forMetric(CoreMetrics.COGNITIVE_COMPLEXITY).on(file)
                .withValue(complexity).save();
    }
}
