package io.sonarswift.cli.pipeline.expr;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionTest {

    private final Expression.Context ctx = Expression.context(
            Map.of("FOO", "bar", "EMPTY", "", "FLAG", "false"),
            true);

    @Test
    void substitutes_env_reference() {
        assertThat(Expression.substitute("hello ${{ env.FOO }}", ctx))
                .isEqualTo("hello bar");
    }

    @Test
    void missing_env_substitutes_empty_string() {
        assertThat(Expression.substitute("x=${{ env.UNSET }}", ctx)).isEqualTo("x=");
    }

    @Test
    void equality_evaluates_to_true_string() {
        assertThat(Expression.substitute("${{ env.FOO == 'bar' }}", ctx)).isEqualTo("true");
        assertThat(Expression.substitute("${{ env.FOO == 'nope' }}", ctx)).isEqualTo("false");
    }

    @Test
    void inequality_evaluates_to_true_string() {
        assertThat(Expression.substitute("${{ env.FOO != 'bar' }}", ctx)).isEqualTo("false");
        assertThat(Expression.substitute("${{ env.FOO != 'nope' }}", ctx)).isEqualTo("true");
    }

    @Test
    void multiple_substitutions_per_input() {
        assertThat(Expression.substitute("${{ env.FOO }}/${{ env.FOO }}", ctx))
                .isEqualTo("bar/bar");
    }

    @Test
    void always_function_is_truthy() {
        assertThat(Expression.isTruthy("always()", ctx)).isTrue();
    }

    @Test
    void success_failure_reflect_context() {
        Expression.Context ok = Expression.context(Map.of(), true);
        Expression.Context bad = Expression.context(Map.of(), false);
        assertThat(Expression.isTruthy("success()", ok)).isTrue();
        assertThat(Expression.isTruthy("failure()", ok)).isFalse();
        assertThat(Expression.isTruthy("success()", bad)).isFalse();
        assertThat(Expression.isTruthy("failure()", bad)).isTrue();
    }

    @Test
    void blank_expression_is_truthy() {
        assertThat(Expression.isTruthy("", ctx)).isTrue();
        assertThat(Expression.isTruthy(null, ctx)).isTrue();
    }

    @Test
    void truthy_treats_false_and_zero_as_falsy() {
        assertThat(Expression.isTruthy("${{ env.FLAG }}", ctx)).isFalse();
        assertThat(Expression.isTruthy("${{ env.EMPTY }}", ctx)).isFalse();
        assertThat(Expression.isTruthy("${{ env.FOO }}", ctx)).isTrue();
    }

    @Test
    void double_quoted_operands_work_too() {
        assertThat(Expression.substitute("${{ env.FOO == \"bar\" }}", ctx))
                .isEqualTo("true");
    }
}
