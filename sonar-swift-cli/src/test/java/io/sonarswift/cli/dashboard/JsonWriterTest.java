package io.sonarswift.cli.dashboard;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonWriterTest {

    @Test
    void writes_null() {
        assertThat(JsonWriter.write(null)).isEqualTo("null");
    }

    @Test
    void writes_booleans() {
        assertThat(JsonWriter.write(true)).isEqualTo("true");
        assertThat(JsonWriter.write(false)).isEqualTo("false");
    }

    @Test
    void writes_numbers_without_quotes() {
        assertThat(JsonWriter.write(42)).isEqualTo("42");
        assertThat(JsonWriter.write(3.14)).isEqualTo("3.14");
        assertThat(JsonWriter.write(0L)).isEqualTo("0");
    }

    @Test
    void writes_strings_with_quotes() {
        assertThat(JsonWriter.write("hi")).isEqualTo("\"hi\"");
        assertThat(JsonWriter.write("")).isEqualTo("\"\"");
    }

    @Test
    void escapes_special_chars_in_strings() {
        assertThat(JsonWriter.write("he said \"hi\"")).isEqualTo("\"he said \\\"hi\\\"\"");
        assertThat(JsonWriter.write("a\\b")).isEqualTo("\"a\\\\b\"");
        assertThat(JsonWriter.write("a\nb")).isEqualTo("\"a\\nb\"");
        assertThat(JsonWriter.write("a\tb")).isEqualTo("\"a\\tb\"");
        assertThat(JsonWriter.write("a\rb")).isEqualTo("\"a\\rb\"");
    }

    @Test
    void escapes_control_chars_with_unicode_escape() {
        //  (start of heading) should be -escaped
        assertThat(JsonWriter.write("ab")).isEqualTo("\"a\\u0001b\"");
    }

    @Test
    void writes_lists_as_arrays() {
        assertThat(JsonWriter.write(List.of(1, 2, 3))).isEqualTo("[1,2,3]");
        assertThat(JsonWriter.write(List.of())).isEqualTo("[]");
        assertThat(JsonWriter.write(List.of("a", "b"))).isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    void writes_maps_as_objects_preserving_insertion_order() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("b", 1);
        m.put("a", 2);
        m.put("c", null);
        assertThat(JsonWriter.write(m)).isEqualTo("{\"b\":1,\"a\":2,\"c\":null}");
    }

    @Test
    void writes_nested_structures() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("name", "sonar-swift");
        root.put("flags", List.of("alpha", "beta"));
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("ok", true);
        nested.put("count", 0);
        root.put("status", nested);
        assertThat(JsonWriter.write(root)).isEqualTo(
                "{\"name\":\"sonar-swift\",\"flags\":[\"alpha\",\"beta\"],\"status\":{\"ok\":true,\"count\":0}}");
    }

    @Test
    void unknown_types_fall_back_to_string_representation() {
        Object obj = new Object() {
            @Override public String toString() { return "custom"; }
        };
        assertThat(JsonWriter.write(obj)).isEqualTo("\"custom\"");
    }
}
