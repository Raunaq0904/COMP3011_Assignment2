/*
 * New file for COMP3011 Assignment 3 (not part of the supplied starter code).
 *
 * Authors:
 *   1. Raunaqdeep Singh Khosla, a1954269
 *
 * AI acknowledgement: this file was developed with the assistance of Claude (Anthropic's generative AI
 * assistant), used as a research, design and development aid. Claude proposed the design and the code,
 * explaining each part step by step. I typed every change in myself, then ran, tested and reviewed it so
 * that I understand what each part does and why it is in the project. All commits were made by me.
 * See the "AI acknowledgement" section of README.md for details.
 */
package comp3011;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader, just big enough for the subtitle server's replies. It avoids adding a library to the player's
 * build. Objects become {@code Map<String, Object>}, arrays become {@code List<Object>}, numbers become
 * {@code Double}, and strings, booleans and null map to their Java equivalents.
 */
final class MiniJson {

    private static final int MAX_DEPTH = 64;

    private final String text;
    private int position;

    private MiniJson(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        MiniJson parser = new MiniJson(text);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        if (parser.position != text.length()) {
            throw parser.error("Unexpected text after the JSON value");
        }
        return value;
    }

    private Object readValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw error("JSON is nested too deeply");
        }
        if (position >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        char c = text.charAt(position);
        return switch (c) {
            case '{' -> readObject(depth);
            case '[' -> readArray(depth);
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        position++; // {
        skipWhitespace();
        if (peek() == '}') {
            position++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a quoted property name");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue(depth + 1));
            skipWhitespace();
            if (peek() == ',') {
                position++;
            } else {
                expect('}');
                return map;
            }
        }
    }

    private List<Object> readArray(int depth) {
        List<Object> list = new ArrayList<>();
        position++; // [
        skipWhitespace();
        if (peek() == ']') {
            position++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue(depth + 1));
            skipWhitespace();
            if (peek() == ',') {
                position++;
            } else {
                expect(']');
                return list;
            }
        }
    }

    private String readString() {
        StringBuilder result = new StringBuilder();
        position++; // opening quote
        while (true) {
            if (position >= text.length()) {
                throw error("Unterminated string");
            }
            char c = text.charAt(position++);
            if (c == '"') {
                return result.toString();
            }
            if (c != '\\') {
                result.append(c);
                continue;
            }
            if (position >= text.length()) {
                throw error("Unterminated escape");
            }
            char escape = text.charAt(position++);
            switch (escape) {
                case '"', '\\', '/' -> result.append(escape);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> {
                    if (position + 4 > text.length()) {
                        throw error("Bad unicode escape");
                    }
                    try {
                        result.append((char) Integer.parseInt(text.substring(position, position + 4), 16));
                    } catch (NumberFormatException e) {
                        throw error("Bad unicode escape");
                    }
                    position += 4;
                }
                default -> throw error("Unknown escape \\" + escape);
            }
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, position)) {
            throw error("Unexpected text");
        }
        position += literal.length();
        return value;
    }

    private Double readNumber() {
        int start = position;
        while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
            position++;
        }
        if (start == position) {
            throw error("Unexpected character '" + text.charAt(position) + "'");
        }
        try {
            return Double.valueOf(text.substring(start, position));
        } catch (NumberFormatException e) {
            throw error("Bad number");
        }
    }

    private void skipWhitespace() {
        while (position < text.length() && Character.isWhitespace(text.charAt(position))) {
            position++;
        }
    }

    private char peek() {
        if (position >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        return text.charAt(position);
    }

    private void expect(char expected) {
        if (peek() != expected) {
            throw error("Expected '" + expected + "'");
        }
        position++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + position);
    }
}