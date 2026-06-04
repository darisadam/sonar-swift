package io.sonarswift.cli.pipeline.expr;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal expression evaluator for pipeline {@code when:} conditions and
 * inline {@code ${{ env.X }}} substitution.
 *
 * <p>Supported forms:</p>
 * <ul>
 *   <li>{@code ${{ env.NAME }}} — substituted with the env value (empty if unset)</li>
 *   <li>{@code ${{ env.NAME != 'value' }}} — comparison → "true" / "false"</li>
 *   <li>{@code ${{ env.NAME == 'value' }}}</li>
 *   <li>{@code always()} — always true (used in {@code if: always()})</li>
 *   <li>{@code success()} — true when no preceding stage failed (resolved by the runner)</li>
 *   <li>{@code failure()} — true when at least one preceding stage failed</li>
 * </ul>
 *
 * <p>Deliberately tiny. We're not building a general expression language.
 * If a pipeline truly needs branching logic it can call a script.</p>
 */
public final class Expression {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{\\{\\s*([^}]+?)\\s*\\}\\}");

    private Expression() {}

    /**
     * Substitute every {@code ${{ … }}} occurrence in the input with its
     * evaluated value.
     */
    public static String substitute(String input, Context ctx) {
        if (input == null || input.isBlank()) return input;
        Matcher m = PLACEHOLDER.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1).trim();
            String value = evaluate(expr, ctx);
            m.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Returns true if the expression evaluates to a truthy value.
     * The empty string and "false" are treated as false; everything else is true.
     */
    public static boolean isTruthy(String expr, Context ctx) {
        if (expr == null || expr.isBlank()) return true;
        String resolved = substitute(expr.startsWith("${{") ? expr : "${{ " + expr + " }}", ctx).trim();
        return !resolved.isBlank()
                && !"false".equalsIgnoreCase(resolved)
                && !"0".equals(resolved)
                && !"no".equalsIgnoreCase(resolved);
    }

    private static String evaluate(String expr, Context ctx) {
        // Built-in functions
        if ("always()".equals(expr)) return "true";
        if ("success()".equals(expr)) return ctx.success() ? "true" : "false";
        if ("failure()".equals(expr)) return ctx.success() ? "false" : "true";

        // env.NAME
        if (expr.startsWith("env.") && !expr.contains(" ")) {
            return ctx.env().getOrDefault(expr.substring(4), "");
        }

        // <lhs> <op> <rhs>
        String[] operators = {"!=", "=="};
        for (String op : operators) {
            int idx = expr.indexOf(op);
            if (idx >= 0) {
                String lhs = expr.substring(0, idx).trim();
                String rhs = expr.substring(idx + op.length()).trim();
                String left = resolveOperand(lhs, ctx);
                String right = resolveOperand(rhs, ctx);
                boolean equal = left.equals(right);
                boolean result = op.equals("==") == equal;
                return result ? "true" : "false";
            }
        }

        // Literal / unknown — return as-is
        return resolveOperand(expr, ctx);
    }

    private static String resolveOperand(String s, Context ctx) {
        if (s.startsWith("'") && s.endsWith("'") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        if (s.startsWith("env.")) {
            return ctx.env().getOrDefault(s.substring(4), "");
        }
        return s;
    }

    /** Evaluation context exposed to expressions. */
    public interface Context {
        Map<String, String> env();
        boolean success();
    }

    /** Anonymous immutable context constructor. */
    public static Context context(Map<String, String> env, boolean success) {
        return new Context() {
            @Override public Map<String, String> env() { return env; }
            @Override public boolean success() { return success; }
        };
    }
}
