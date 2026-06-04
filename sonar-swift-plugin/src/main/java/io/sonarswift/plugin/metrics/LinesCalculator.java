package io.sonarswift.plugin.metrics;

import io.sonarswift.plugin.parser.Token;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes line-based metrics from a token stream:
 *
 * <ul>
 *   <li>{@link CoreMetrics#NCLOC} — lines containing any code token</li>
 *   <li>{@link CoreMetrics#COMMENT_LINES} — lines containing only comment tokens</li>
 * </ul>
 *
 * <p>{@code lines} (physical lines) comes from {@link InputFile#lines()} — we
 * don't recompute it.</p>
 */
public class LinesCalculator {

    public void compute(InputFile file, List<Token> tokens, SensorContext context) {
        Set<Integer> codeLines = new HashSet<>();
        Set<Integer> commentLines = new HashSet<>();

        for (Token t : tokens) {
            if (t.kind() == Token.Kind.OTHER) continue;

            if (t.isComment()) {
                for (int l = t.line(); l <= t.endLine(); l++) {
                    commentLines.add(l);
                }
            } else {
                // STRING_LITERAL spanning lines: count opener + closer only — the
                // pure-content lines aren't code.
                if (t.kind() == Token.Kind.STRING_LITERAL && t.line() != t.endLine()) {
                    codeLines.add(t.line());
                    codeLines.add(t.endLine());
                } else {
                    for (int l = t.line(); l <= t.endLine(); l++) {
                        codeLines.add(l);
                    }
                }
            }
        }

        // A line counts as comment only if it has no code on it.
        commentLines.removeAll(codeLines);

        context.<Integer>newMeasure().forMetric(CoreMetrics.NCLOC).on(file)
                .withValue(codeLines.size()).save();
        context.<Integer>newMeasure().forMetric(CoreMetrics.COMMENT_LINES).on(file)
                .withValue(commentLines.size()).save();
    }
}
