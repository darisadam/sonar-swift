package io.sonarswift.plugin.sensors;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.metrics.CognitiveComplexityCalculator;
import io.sonarswift.plugin.metrics.CyclomaticComplexityCalculator;
import io.sonarswift.plugin.metrics.LinesCalculator;
import io.sonarswift.plugin.metrics.StructuralMetricsCalculator;
import io.sonarswift.plugin.parser.SwiftTokenizer;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SuppressionRegistry;
import io.sonarswift.plugin.rules.SwiftCheck;
import io.sonarswift.plugin.rules.SwiftCheckList;

import org.sonar.api.batch.fs.FilePredicates;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.measure.Metric;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.cpd.NewCpdTokens;
import org.sonar.api.measures.CoreMetrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The main native-analysis sensor for Swift. For every Swift {@link InputFile}:
 *
 * <ol>
 *   <li>Tokenize the source</li>
 *   <li>Compute and save metrics (LOC, comments, complexity, structural)</li>
 *   <li>Emit CPD tokens</li>
 *   <li>Run every active check that has a token visitor</li>
 *   <li>(optionally) request AST via {@link io.sonarswift.plugin.parser.SwiftSyntaxClient}
 *       and run checks that need AST</li>
 * </ol>
 */
public class SwiftSensor implements Sensor {

    private static final Logger LOG = LoggerFactory.getLogger(SwiftSensor.class);

    @Override
    public void describe(SensorDescriptor descriptor) {
        descriptor
                .name("Sonar Swift — native analysis")
                .onlyOnLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY);
    }

    @Override
    public void execute(SensorContext context) {
        FileSystem fs = context.fileSystem();
        FilePredicates p = fs.predicates();
        Iterable<InputFile> swiftFiles = fs.inputFiles(p.and(
                p.hasLanguage(SwiftPluginConstants.SWIFT_LANGUAGE_KEY),
                p.hasType(InputFile.Type.MAIN)));

        List<SwiftCheck> activeChecks = instantiateActiveChecks(context);

        int n = 0;
        for (InputFile file : swiftFiles) {
            try {
                analyzeFile(context, file, activeChecks);
                n++;
            } catch (Exception e) {
                LOG.warn("Failed to analyze {}: {}", file, e.getMessage());
            }
        }
        LOG.info("Sonar Swift native sensor: analyzed {} Swift file(s)", n);
    }

    private void analyzeFile(SensorContext context, InputFile file, List<SwiftCheck> checks)
            throws IOException {
        String source = Files.readString(file.path(), file.charset());
        List<Token> tokens = SwiftTokenizer.tokenize(source);

        // Metrics
        new LinesCalculator().compute(file, tokens, context);
        new CyclomaticComplexityCalculator().compute(file, tokens, context);
        new CognitiveComplexityCalculator().compute(file, tokens, context);
        new StructuralMetricsCalculator().compute(file, tokens, context);

        // CPD tokens
        emitCpdTokens(file, tokens, context);

        // Pre-compute per-file suppression registry once, share across checks
        SuppressionRegistry suppressions = new SuppressionRegistry(file, tokens);

        // Run token-based checks
        for (SwiftCheck check : checks) {
            try {
                check.setSuppressions(suppressions);
                check.visitTokens(file, tokens, context);
            } catch (Exception e) {
                LOG.warn("Check {} threw on {}: {}", check.ruleKey(), file, e.getMessage());
            }
        }
    }

    private void emitCpdTokens(InputFile file, List<Token> tokens, SensorContext context) {
        NewCpdTokens cpd = context.newCpdTokens().onFile(file);
        for (Token t : tokens) {
            if (!t.isCode()) continue;
            // Skip imports (top-level)
            if (t.kind() == Token.Kind.KEYWORD && "import".equals(t.text())) {
                continue;
            }
            // Normalize identifiers/literals
            String text = switch (t.kind()) {
                case IDENT -> "$IDENT";
                case INT_LITERAL -> "$INT";
                case FLOAT_LITERAL -> "$FLOAT";
                case STRING_LITERAL -> "$STR";
                case BOOL_LITERAL -> "$BOOL";
                case NIL_LITERAL -> "$NIL";
                default -> t.text();
            };
            cpd.addToken(
                    file.newRange(t.line(), t.col() - 1, t.endLine(), t.endCol() - 1),
                    text);
        }
        cpd.save();
    }

    private List<SwiftCheck> instantiateActiveChecks(SensorContext context) {
        List<SwiftCheck> out = new ArrayList<>();
        for (Class<?> klass : SwiftCheckList.CHECK_CLASSES) {
            try {
                SwiftCheck instance = (SwiftCheck) klass.getDeclaredConstructor().newInstance();
                // Check that its rule is active in the current profile
                if (context.activeRules().find(
                        org.sonar.api.rule.RuleKey.of(
                                SwiftPluginConstants.SWIFT_REPOSITORY_KEY,
                                instance.ruleKey())) != null) {
                    out.add(instance);
                }
            } catch (ReflectiveOperationException e) {
                LOG.warn("Failed to instantiate {}: {}", klass.getName(), e.getMessage());
            }
        }
        LOG.debug("Instantiated {} active Swift checks", out.size());
        return out;
    }
}
