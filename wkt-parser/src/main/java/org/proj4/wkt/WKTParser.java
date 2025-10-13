package org.proj4.wkt;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Well-Known Text (WKT) parser for coordinate reference systems.
 * Ported from the JavaScript wkt-parser library.
 */
public class WKTParser {
    
    // Parser states
    private static final int NEUTRAL = 1;
    private static final int KEYWORD = 2;
    private static final int NUMBER = 3;
    private static final int QUOTED = 4;
    private static final int AFTERQUOTE = 5;
    private static final int ENDED = -1;
    
    // Regular expressions
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("[A-Za-z84_]");
    private static final Pattern END_THINGS = Pattern.compile("[,]");
    private static final Pattern DIGITS = Pattern.compile("[\\d\\.E\\-\\+]");
    
    private String text;
    private int level;
    private int place;
    private List<Object> root;
    private Stack<List<Object>> stack;
    private List<Object> currentObject;
    private int state;
    private String word;
    
    /**
     * Creates a new WKT parser for the given text.
     * @param text the WKT string to parse
     * @throws IllegalArgumentException if text is not a string
     */
    public WKTParser(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text cannot be null");
        }
        this.text = text.trim();
        this.level = 0;
        this.place = 0;
        this.root = null;
        this.stack = new Stack<>();
        this.currentObject = null;
        this.state = NEUTRAL;
        this.word = null;
    }
    
    /**
     * Parses the WKT string and returns the parsed structure.
     * @return the parsed WKT structure
     * @throws WKTParseException if parsing fails
     */
    public List<Object> parse() throws WKTParseException {
        while (place < text.length()) {
            readCharacter();
        }
        if (state == ENDED) {
            return root;
        }
        throw new WKTParseException("Unable to parse string \"" + text + "\". State is " + state);
    }
    
    /**
     * Reads the next character and processes it according to the current state.
     */
    private void readCharacter() throws WKTParseException {
        if (place >= text.length()) {
            return;
        }
        
        char ch = text.charAt(place++);
        
        // Skip whitespace unless we're in quoted state
        if (state != QUOTED) {
            while (WHITESPACE.matcher(String.valueOf(ch)).matches()) {
                if (place >= text.length()) {
                    return;
                }
                ch = text.charAt(place++);
            }
        }
        
        switch (state) {
            case NEUTRAL:
                neutral(ch);
                break;
            case KEYWORD:
                keyword(ch);
                break;
            case QUOTED:
                quoted(ch);
                break;
            case AFTERQUOTE:
                afterQuote(ch);
                break;
            case NUMBER:
                number(ch);
                break;
            case ENDED:
                return;
        }
    }
    
    /**
     * Handles characters in the NEUTRAL state.
     */
    private void neutral(char ch) throws WKTParseException {
        if (LATIN.matcher(String.valueOf(ch)).matches()) {
            word = String.valueOf(ch);
            state = KEYWORD;
            return;
        }
        if (ch == '"') {
            word = "";
            state = QUOTED;
            return;
        }
        if (DIGITS.matcher(String.valueOf(ch)).matches()) {
            word = String.valueOf(ch);
            state = NUMBER;
            return;
        }
        if (END_THINGS.matcher(String.valueOf(ch)).matches() || ch == ']') {
            afterItem(ch);
            return;
        }
        throw new WKTParseException("Haven't handled \"" + ch + "\" in neutral yet, index " + place);
    }
    
    /**
     * Handles characters in the KEYWORD state.
     */
    private void keyword(char ch) throws WKTParseException {
        if (KEYWORD_PATTERN.matcher(String.valueOf(ch)).matches()) {
            word += ch;
            return;
        }
        if (ch == '[') {
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
        if (END_THINGS.matcher(String.valueOf(ch)).matches() || ch == ']') {
            afterItem(ch);
            return;
        }
        throw new WKTParseException("Haven't handled \"" + ch + "\" in keyword yet, index " + place);
    }
    
    /**
     * Handles characters in the QUOTED state.
     */
    private void quoted(char ch) {
        if (ch == '"') {
            state = AFTERQUOTE;
            return;
        }
        word += ch;
    }
    
    /**
     * Handles characters in the AFTERQUOTE state.
     */
    private void afterQuote(char ch) throws WKTParseException {
        if (ch == '"') {
            word += '"';
            state = QUOTED;
            return;
        }
        if (END_THINGS.matcher(String.valueOf(ch)).matches() || ch == ']') {
            word = word.trim();
            afterItem(ch);
            return;
        }
        throw new WKTParseException("Haven't handled \"" + ch + "\" in afterquote yet, index " + place);
    }
    
    /**
     * Handles characters in the NUMBER state.
     */
    private void number(char ch) throws WKTParseException {
        if (DIGITS.matcher(String.valueOf(ch)).matches()) {
            word += ch;
            return;
        }
        if (END_THINGS.matcher(String.valueOf(ch)).matches() || ch == ']') {
            try {
                Double number = Double.parseDouble(word);
                // Store as the actual number object, not a string representation
                currentObject.add(number);
                word = null;
            } catch (NumberFormatException e) {
                throw new WKTParseException("Invalid number: " + word);
            }
            state = NEUTRAL;
            if (ch == ']') {
                level--;
                currentObject = stack.pop();
                if (currentObject == null) {
                    state = ENDED;
                }
            }
            return;
        }
        throw new WKTParseException("Haven't handled \"" + ch + "\" in number yet, index " + place);
    }
    
    /**
     * Handles the end of an item.
     */
    private void afterItem(char ch) throws WKTParseException {
        if (ch == ',') {
            if (word != null) {
                currentObject.add(word);
            }
            word = null;
            state = NEUTRAL;
            return;
        }
        if (ch == ']') {
            level--;
            if (word != null) {
                currentObject.add(word);
                word = null;
            }
            state = NEUTRAL;
            currentObject = stack.pop();
            if (currentObject == null) {
                state = ENDED;
            }
            return;
        }
    }
    
    /**
     * Static method to parse a WKT string.
     * @param wktString the WKT string to parse
     * @return the parsed WKT structure
     * @throws WKTParseException if parsing fails
     */
    public static List<Object> parseString(String wktString) throws WKTParseException {
        WKTParser parser = new WKTParser(wktString);
        return parser.parse();
    }
}
