package com.mealapp.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JsonUtil
 * --------
 * A compact recursive-descent JSON parser plus a matching serializer.
 * Kept dependency-free on purpose (no Jackson/Gson) since this project's
 * whole point is "plain Java, nothing else." Parses into java.util.Map /
 * java.util.List / String / Double / Boolean / null, which is all the
 * request/response shapes in this API need.
 */
public final class JsonUtil {
    private JsonUtil() { }

    // ---------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------
    public static Object parse(String input) {
        if (input == null || input.isBlank()) return null;
        Parser p = new Parser(input);
        p.skipWhitespace();
        Object value = p.parseValue();
        p.skipWhitespace();
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String input) {
        Object v = parse(input);
        if (v instanceof Map) return (Map<String, Object>) v;
        return new LinkedHashMap<>();
    }

    private static final class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) { this.s = s; }

        void skipWhitespace() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        char peek() {
            if (pos >= s.length()) throw new RuntimeException("Unexpected end of JSON input");
            return s.charAt(pos);
        }

        Object parseValue() {
            skipWhitespace();
            char c = peek();
            switch (c) {
                case '{': return parseObjectValue();
                case '[': return parseArray();
                case '"': return parseString();
                case 't': expect("true"); return Boolean.TRUE;
                case 'f': expect("false"); return Boolean.FALSE;
                case 'n': expect("null"); return null;
                default: return parseNumber();
            }
        }

        void expect(String literal) {
            if (pos + literal.length() > s.length() || !s.startsWith(literal, pos)) {
                throw new RuntimeException("Invalid JSON literal near position " + pos);
            }
            pos += literal.length();
        }

        Map<String, Object> parseObjectValue() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // consume '{'
            skipWhitespace();
            if (peek() == '}') { pos++; return map; }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (peek() != ':') throw new RuntimeException("Expected ':' near position " + pos);
                pos++;
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char next = peek();
                if (next == ',') { pos++; continue; }
                if (next == '}') { pos++; break; }
                throw new RuntimeException("Expected ',' or '}' near position " + pos);
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // consume '['
            skipWhitespace();
            if (peek() == ']') { pos++; return list; }
            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                char next = peek();
                if (next == ',') { pos++; continue; }
                if (next == ']') { pos++; break; }
                throw new RuntimeException("Expected ',' or ']' near position " + pos);
            }
            return list;
        }

        String parseString() {
            if (peek() != '"') throw new RuntimeException("Expected string near position " + pos);
            pos++;
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') break;
                if (c == '\\') {
                    char esc = s.charAt(pos++);
                    switch (esc) {
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'n': sb.append('\n'); break;
                        case 'r': sb.append('\r'); break;
                        case 't': sb.append('\t'); break;
                        case 'u':
                            String hex = s.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default: sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        Object parseNumber() {
            int start = pos;
            if (peek() == '-') pos++;
            while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.'
                    || s.charAt(pos) == 'e' || s.charAt(pos) == 'E' || s.charAt(pos) == '+' || s.charAt(pos) == '-')) {
                pos++;
            }
            String numStr = s.substring(start, pos);
            if (numStr.isEmpty()) throw new RuntimeException("Invalid JSON near position " + pos);
            return Double.parseDouble(numStr);
        }
    }

    // ---------------------------------------------------------------
    // Serialization
    // ---------------------------------------------------------------
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String str) {
            writeString(str, sb);
        } else if (value instanceof Boolean b) {
            sb.append(b.toString());
        } else if (value instanceof Number n) {
            writeNumber(n, sb);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                writeString(String.valueOf(entry.getKey()), sb);
                sb.append(':');
                writeValue(entry.getValue(), sb);
            }
            sb.append('}');
        } else if (value instanceof Iterable<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) sb.append(',');
                first = false;
                writeValue(item, sb);
            }
            sb.append(']');
        } else if (value instanceof Object[] arr) {
            writeValue(java.util.Arrays.asList(arr), sb);
        } else {
            writeString(value.toString(), sb);
        }
    }

    private static void writeNumber(Number n, StringBuilder sb) {
        double d = n.doubleValue();
        if (d == Math.floor(d) && !Double.isInfinite(d) && Math.abs(d) < 1e15) {
            sb.append((long) d);
        } else {
            sb.append(d);
        }
    }

    private static void writeString(String str, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    // ---------------------------------------------------------------
    // Convenience getters (JSON numbers parse as Double; these coerce)
    // ---------------------------------------------------------------
    public static String getString(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    public static Double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (NumberFormatException e) { return null; }
    }

    public static Integer getInt(Map<String, Object> map, String key, int fallback) {
        Double d = getDouble(map, key);
        return d == null ? fallback : d.intValue();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof List) return (List<Object>) v;
        return new ArrayList<>();
    }
}
