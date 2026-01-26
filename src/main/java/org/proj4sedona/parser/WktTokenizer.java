package org.proj4sedona.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * State-machine tokenizer for WKT (Well-Known Text) strings.
 * Mirrors: wkt-parser/parser.js
 * 
 * Parses WKT strings like:
 *   PROJCS["name", GEOGCS["WGS 84", ...], PROJECTION["Mercator"], ...]
 * 
 * Into nested List structures:
 *   ["PROJCS", "name", ["GEOGCS", "WGS 84", ...], ["PROJECTION", "Mercator"], ...]
 */
public class WktTokenizer {

    // Parser states
    private static final int NEUTRAL = 1;
    private static final int KEYWORD = 2;
    private static final int NUMBER = 3;
    private static final int QUOTED = 4;
    private static final int AFTERQUOTE = 5;
    private static final int ENDED = -1;

    // Character patterns
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern KEYWORD_CHAR = Pattern.compile("[A-Za-z84_]");
    private static final Pattern END_THINGS = Pattern.compile("[,\\]]");
    private static final Pattern DIGITS = Pattern.compile("[\\d.E\\-+]");

    // Parser state
    private String text;
    private int level;
    private int place;
    private List<Object> root;
    private Stack<List<Object>> stack;
    private List<Object> currentObject;
    private int state;
    private String word;

    /**
     * Parse a WKT string into a nested List structure.
     * 
     * @param wkt The WKT string to parse
     * @return Nested List representing the WKT structure
     * @throws IllegalArgumentException if the string cannot be parsed
     */
    public List<Object> parse(String wkt) {
        if (wkt == null) {
            throw new IllegalArgumentException("WKT string cannot be null");
        }
        
        // Initialize parser state
        this.text = wkt.trim();
        this.level = 0;
        this.place = 0;
        this.root = null;
        this.stack = new Stack<>();
        this.currentObject = null;
        this.state = NEUTRAL;
        this.word = null;

        // Parse character by character
        while (place < text.length()) {
            readCharacter();
        }

        if (state == ENDED) {
            return root;
        }

        throw new IllegalArgumentException("Unable to parse WKT string: \"" + text + "\". State is " + state);
    }

    /**
     * Read and process the next character.
     */
    private void readCharacter() {
        if (place >= text.length()) {
            return;
        }

        char c = text.charAt(place++);

        // Skip whitespace except in quoted strings
        if (state != QUOTED) {
            while (isWhitespace(c)) {
                if (place >= text.length()) {
                    return;
                }
                c = text.charAt(place++);
            }
        }

        switch (state) {
            case NEUTRAL:
                neutral(c);
                break;
            case KEYWORD:
                keyword(c);
                break;
            case QUOTED:
                quoted(c);
                break;
            case AFTERQUOTE:
                afterquote(c);
                break;
            case NUMBER:
                number(c);
                break;
            case ENDED:
                // Do nothing
                break;
            default:
                throw new IllegalStateException("Unknown parser state: " + state);
        }
    }

    /**
     * Handle character in NEUTRAL state.
     */
    private void neutral(char c) {
        if (isLatin(c)) {
            word = String.valueOf(c);
            state = KEYWORD;
            return;
        }
        if (c == '"') {
            word = "";
            state = QUOTED;
            return;
        }
        if (isDigit(c)) {
            word = String.valueOf(c);
            state = NUMBER;
            return;
        }
        if (isEndThing(c)) {
            afterItem(c);
            return;
        }
        throw new IllegalArgumentException("Haven't handled \"" + c + "\" in neutral yet, index " + place);
    }

    /**
     * Handle character in KEYWORD state.
     */
    private void keyword(char c) {
        if (isKeywordChar(c)) {
            word += c;
            return;
        }
        if (c == '[') {
            List<Object> newObjects = new ArrayList<>();
            newObjects.add(word);
            level++;
            if (root == null) {
                root = newObjects;
            } else {
                currentObject.add(newObjects);
            }
            stack.push(currentObject);
            currentObject = newObjects;
            state = NEUTRAL;
            return;
        }
        if (isEndThing(c)) {
            afterItem(c);
            return;
        }
        throw new IllegalArgumentException("Haven't handled \"" + c + "\" in keyword yet, index " + place);
    }

    /**
     * Handle character in QUOTED state.
     */
    private void quoted(char c) {
        if (c == '"') {
            state = AFTERQUOTE;
            return;
        }
        word += c;
    }

    /**
     * Handle character in AFTERQUOTE state.
     */
    private void afterquote(char c) {
        // Handle escaped quote ("")
        if (c == '"') {
            word += '"';
            state = QUOTED;
            return;
        }
        if (isEndThing(c)) {
            word = word.trim();
            afterItem(c);
            return;
        }
        throw new IllegalArgumentException("Haven't handled \"" + c + "\" in afterquote yet, index " + place);
    }

    /**
     * Handle character in NUMBER state.
     */
    private void number(char c) {
        if (isDigit(c)) {
            word += c;
            return;
        }
        if (isEndThing(c)) {
            // Convert to number
            try {
                currentObject.add(Double.parseDouble(word));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number: " + word, e);
            }
            word = null;
            afterItem(c);
            return;
        }
        throw new IllegalArgumentException("Haven't handled \"" + c + "\" in number yet, index " + place);
    }

    /**
     * Handle end of item (comma or closing bracket).
     */
    private void afterItem(char c) {
        if (c == ',') {
            if (word != null) {
                currentObject.add(word);
            }
            word = null;
            state = NEUTRAL;
            return;
        }
        if (c == ']') {
            level--;
            if (word != null) {
                currentObject.add(word);
                word = null;
            }
            state = NEUTRAL;
            currentObject = stack.isEmpty() ? null : stack.pop();
            if (currentObject == null) {
                state = ENDED;
            }
        }
    }

    // Character classification helpers

    private boolean isWhitespace(char c) {
        return WHITESPACE.matcher(String.valueOf(c)).matches();
    }

    private boolean isLatin(char c) {
        return LATIN.matcher(String.valueOf(c)).matches();
    }

    private boolean isKeywordChar(char c) {
        return KEYWORD_CHAR.matcher(String.valueOf(c)).matches();
    }

    private boolean isEndThing(char c) {
        return END_THINGS.matcher(String.valueOf(c)).matches();
    }

    private boolean isDigit(char c) {
        return DIGITS.matcher(String.valueOf(c)).matches();
    }
}
