package org.hammer.wikihelp.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) throw parser.error("Unexpected trailing input");
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw new IllegalArgumentException("Expected JSON object but got " + type(value));
    }

    @SuppressWarnings("unchecked")
    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) return (List<Object>) list;
        throw new IllegalArgumentException("Expected JSON array but got " + type(value));
    }

    public static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    public static long longValue(Object value, long defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    public static Map<String, Object> object(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value == null ? Map.of() : object(value);
    }

    public static List<Object> array(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value == null ? List.of() : array(value);
    }

    private static String type(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            this.text = text == null ? "" : text;
        }

        private Object readValue() {
            skipWhitespace();
            if (atEnd()) throw error("Expected value");
            return switch (text.charAt(index)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                skipWhitespace();
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (peek('}')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            ArrayList<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return result;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!atEnd()) {
                char c = text.charAt(index++);
                if (c == '"') return result.toString();
                if (c == '\\') {
                    if (atEnd()) throw error("Incomplete escape");
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                        case '"', '\\', '/' -> result.append(escaped);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(readUnicode());
                        default -> throw error("Invalid escape \\" + escaped);
                    }
                } else {
                    result.append(c);
                }
            }
            throw error("Unterminated string");
        }

        private char readUnicode() {
            if (index + 4 > text.length()) throw error("Incomplete unicode escape");
            String hex = text.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException exception) {
                throw error("Invalid unicode escape " + hex);
            }
        }

        private Object readNumber() {
            int start = index;
            if (peek('-')) index++;
            readDigits();
            if (peek('.')) {
                index++;
                readDigits();
            }
            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) index++;
                readDigits();
            }
            if (start == index) throw error("Expected number");
            try {
                return new BigDecimal(text.substring(start, index));
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private void readDigits() {
            int start = index;
            while (!atEnd() && Character.isDigit(text.charAt(index))) index++;
            if (start == index) throw error("Expected digit");
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, index)) throw error("Expected " + literal);
            index += literal.length();
            return value;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (atEnd() || text.charAt(index) != expected) throw error("Expected '" + expected + "'");
            index++;
        }

        private boolean peek(char expected) {
            return !atEnd() && text.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(index))) index++;
        }

        private boolean atEnd() {
            return index >= text.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at character " + index);
        }
    }
}
