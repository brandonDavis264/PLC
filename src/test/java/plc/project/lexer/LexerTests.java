package plc.project.lexer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

public final class LexerTests {

    @ParameterizedTest
    @MethodSource
    void testWhitespace(String test, String input, boolean success) {
        test(input, List.of(), success);
    }

    public static Stream<Arguments> testWhitespace() {
        return Stream.of(
            Arguments.of("Space", " ", true),
            Arguments.of("All Characters", "\b\n\r\t ", true),
            Arguments.of("Space Character", "\s", true),
            Arguments.of("Newline", "\n", true),
            Arguments.of("Multiple", "   \n   ", true),
            Arguments.of("Carriage Return", "\r", true),
            Arguments.of("New Page", "\f", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testComment(String test, String input, boolean success) {
        test(input, List.of(), success);
    }

    public static Stream<Arguments> testComment() {
        return Stream.of(
            Arguments.of("Comment", "//comment", true),
            Arguments.of("Empty Comment", "//", true),
            Arguments.of("Multiple", "//first\n//second", true)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIdentifier(String test, String input, boolean success) {
        test(input, List.of(new Token(Token.Type.IDENTIFIER, input)), success);
    }

    public static Stream<Arguments> testIdentifier() {
        return Stream.of(
            Arguments.of("Alphabetic", "getName", true),
            Arguments.of("Alphanumeric", "thelegend27", true),
            Arguments.of("Underscorse and Dashes", "the_legend-27", true),
            Arguments.of("Leading Underscore", "_the_legend-27", true),
            Arguments.of("A Underscore", "_", true),
            Arguments.of("Special Charaters", "1egend$-27", false),
            Arguments.of("Leading Hyphen", "-five", false),
            Arguments.of("Hyphen", "four-five", true),
            Arguments.of("Leading Digit", "1fish2fish", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testInteger(String test, String input, boolean success) {
        test(input, List.of(new Token(Token.Type.INTEGER, input)), success);
    }

    public static Stream<Arguments> testInteger() {
        return Stream.of(
            Arguments.of("Single Digit", "1", true),
            Arguments.of("Multiple Digits", "123", true),
            Arguments.of("Zero", "0", true),
            Arguments.of("Positive Integer", "+200", true),
            Arguments.of("Negative Integer", "-301", true),
            Arguments.of("Exponent Pos", "2e+45", true),
            Arguments.of(" Exponent Neg", "2e-45", true),
            Arguments.of("Decimal Exponent", "2e45.6", false),
            Arguments.of("Exponent", "1e10", true),
            Arguments.of("Missing Exponent Digits", "1e", false),
            Arguments.of("Incorrect Symbol exponent", "1E23", false)

        );
    }

    @ParameterizedTest
    @MethodSource
    void testDecimal(String test, String input, boolean success) {
        test(input, List.of(new Token(Token.Type.DECIMAL, input)), success);
    }

    public static Stream<Arguments> testDecimal() {
        return Stream.of(
            Arguments.of("Integer", "1", false),
            Arguments.of("Zero", "+0.0e0", true),
            Arguments.of("Postive Decimal", "+1.0", true),
            Arguments.of("Negative Decimal", "11.0", true),
            Arguments.of("Multiple Digits", "123.456", true),
            Arguments.of("Exponent", "1.0e10", true),
            Arguments.of("Leading and trailing Zeros", "04504.00210", true),
            Arguments.of("Extreme Input", "000600067600.089098900e004", true),
            Arguments.of("Missing int Part", ".5", false),
            Arguments.of("Exponent Pos", "2.0e+45", true),
            Arguments.of("Decimal Exponent Neg", "2.0e-45", true),
            Arguments.of("Decimal Exponent", "2.0e45.6", false),
            Arguments.of("Trailing Decimal", "1.", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testCharacter(String test, String input, boolean success) {
        test(input, List.of(new Token(Token.Type.CHARACTER, input)), success);
    }

    public static Stream<Arguments> testCharacter() {
        return Stream.of(
            Arguments.of("Alphabetic", "\'c\'", true),
            Arguments.of("Newline Escape", "\'\\n\'", true),
            Arguments.of("Special Charcter", "\'%\'", true),
            Arguments.of("Backslash Escape", "'\\\\'", true),
            Arguments.of("Empty", "\'\'", false),

            Arguments.of("Invalid Escape", "\'\\p\'", false),
            Arguments.of("Unterminated", "\'u", false),
            Arguments.of("odd amount of ' ", "\'\'\'", false),
            Arguments.of("Multiple", "\'abc\'", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testString(String test, String input, boolean success) {
        test(input, List.of(new Token(Token.Type.STRING, input)), success);
    }

    public static Stream<Arguments> testString() {
        return Stream.of(
            Arguments.of("Empty", "\"\"", true),
            Arguments.of("Whitespace", "\"      \"", true),
            Arguments.of("Alphabetic", "\"string\"", true),
            Arguments.of("Newline Escape", "\"Hello,\\nWorld\"", true),
            Arguments.of("Invalid Escape", "\"invalid\\escape\"", false),

            //Empty
            Arguments.of("Empty String", "\"\"", true),
            Arguments.of("Standard String", "\"Hello World\"", true),
            Arguments.of("Escape Characters No Space", "\"\\\\\\\\\\\\\\\\\"", true),
            //All Escape Characters
            Arguments.of("All Escape Characters", "\"\\b\\n\\r\\t\\\\\\\"\\'\"", true),
            //Exteme test
            Arguments.of("Extreme Test", "\"This is a really long string with special characters like $, \\n, \\r, and quotes: \\\"\"", true),
            //Trailing WhiteSpace
            Arguments.of("Trailing White Space", "\"  Hello  \"", true),
            // String Containing escape
            Arguments.of("Contianing an Escape", "\"Hello \\n Wrold\"", true),

            //Ask About \f
            Arguments.of("No Quotes", "string", false),
            //Invalid Escape Character
            Arguments.of("Invalid Escape Character", "\" \\p \"", false),
            //New Line
            Arguments.of("Invalid New Line", "\"Hello\nWorld\"", false),

            Arguments.of("Invalid New Line", "\"first␤second\"", true),

            //Carrige Return
            Arguments.of("Invalid Carrige Return", "\"Hello\rWorld\"", false),
            //missing end quote
            Arguments.of("Missing A Quote", "\"", false),
            //Unescaped Double Quote Inside String
            Arguments.of("Too many Quotes", "\"\"\"", false)

        );
    }

    @ParameterizedTest
    @MethodSource
    void testOperator(String test, String input, boolean success) {
        test(input, List.of(new Token(Token.Type.OPERATOR, input)), success);
    }

    public static Stream<Arguments> testOperator() {
        return Stream.of(
            Arguments.of("Character", "(", true),
            Arguments.of("Comparison", "<=", true),
            Arguments.of("Special Char", "?", true),
            Arguments.of("Special Char", ".", true),

            Arguments.of("WhiteSpace Char", "\n", false)
        );
    }

    @ParameterizedTest
    @MethodSource
    void testInteraction(String test, String input, List<Token> expected) {
        test(input, expected, true);
    }

    public static Stream<Arguments> testInteraction() {
        return Stream.of(
            Arguments.of("Whitespace", "first second", List.of(
                new Token(Token.Type.IDENTIFIER, "first"),
                new Token(Token.Type.IDENTIFIER, "second")
            )),
            Arguments.of("Identifier Leading Hyphen", "-five", List.of(
                new Token(Token.Type.OPERATOR, "-"),
                new Token(Token.Type.IDENTIFIER, "five")
            )),
            Arguments.of("Identifier Leading Digit", "1fish2fish", List.of(
                new Token(Token.Type.INTEGER, "1"),
                new Token(Token.Type.IDENTIFIER, "fish2fish")
            )),
            Arguments.of("Integer Missing Exponent Digits", "1e", List.of(
                new Token(Token.Type.INTEGER, "1"),
                new Token(Token.Type.IDENTIFIER, "e")
            )),
            Arguments.of("Decimal Missing Decimal Digits", "1.", List.of(
                new Token(Token.Type.INTEGER, "1"),
                new Token(Token.Type.OPERATOR, ".")
            )),
            Arguments.of("Operator Multiple Operators", "<=>", List.of(
                new Token(Token.Type.OPERATOR, "<="),
                new Token(Token.Type.OPERATOR, ">")
            )),
            Arguments.of("String Multiple Operators", "\"\"identifier\"\"", List.of(
                    new Token(Token.Type.STRING, "\"\""),
                    new Token(Token.Type.IDENTIFIER, "identifier"),
                    new Token(Token.Type.STRING, "\"\"")
            )),
            Arguments.of("Double Decimal", "123.123.123", List.of(
                    new Token(Token.Type.DECIMAL, "123.123"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.INTEGER, "123")
            ))
        );
    }

    @ParameterizedTest
    @MethodSource
    void testException(String test, String input) {
        Assertions.assertThrows(LexException.class, () -> new Lexer(input).lex());
    }

    public static Stream<Arguments> testException() {
        return Stream.of(
            Arguments.of("Character Unterminated", "\'u"),
            Arguments.of("Character Multiple", "\'abc\'"),
            Arguments.of("String Unterminated", "\"u"),
            Arguments.of("String Invalid Escape", "\"invalid\\escape\"")
        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, String input, List<Token> expected) {
        test(input, expected, true);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Variable", "LET x = 5;", List.of(
                new Token(Token.Type.IDENTIFIER, "LET"),
                new Token(Token.Type.IDENTIFIER, "x"),
                new Token(Token.Type.OPERATOR, "="),
                new Token(Token.Type.INTEGER, "5"),
                new Token(Token.Type.OPERATOR, ";")
            )),
            Arguments.of("Print Function", "print(\"Hello, World!\");", List.of(
                new Token(Token.Type.IDENTIFIER, "print"),
                new Token(Token.Type.OPERATOR, "("),
                new Token(Token.Type.STRING, "\"Hello, World!\""),
                new Token(Token.Type.OPERATOR, ")"),
                new Token(Token.Type.OPERATOR, ";")
            )),
            Arguments.of("Print Function", "identifier 1 1.0 'c' \"string\" $", List.of(
                    new Token(Token.Type.IDENTIFIER, "identifier"),
                    new Token(Token.Type.INTEGER, "1"),
                    new Token(Token.Type.DECIMAL, "1.0"),
                    new Token(Token.Type.CHARACTER, "'c'"),
                    new Token(Token.Type.STRING, "\"string\""),
                    new Token(Token.Type.OPERATOR, "$")
            )),
            Arguments.of("Loop", "for(int i = 0; i < str.length(); i++)", List.of(
                    new Token(Token.Type.IDENTIFIER, "for"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.IDENTIFIER, "int"),
                    new Token(Token.Type.IDENTIFIER, "i"),
                    new Token(Token.Type.OPERATOR, "="),
                    new Token(Token.Type.INTEGER, "0"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "i"),
                    new Token(Token.Type.OPERATOR, "<"),
                    new Token(Token.Type.IDENTIFIER, "str"),
                    new Token(Token.Type.OPERATOR, "."),
                    new Token(Token.Type.IDENTIFIER, "length"),
                    new Token(Token.Type.OPERATOR, "("),
                    new Token(Token.Type.OPERATOR, ")"),
                    new Token(Token.Type.OPERATOR, ";"),
                    new Token(Token.Type.IDENTIFIER, "i"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.OPERATOR, "+"),
                    new Token(Token.Type.OPERATOR, ")")
            ))
        );
    }

    private static void test(String input, List<Token> expected, boolean success) {
        if (success) {
            var tokens = Assertions.assertDoesNotThrow(() -> new Lexer(input).lex());
            Assertions.assertEquals(expected, tokens);
        } else {
            //Consider both different results or exceptions to be acceptable.
            //This is a bit lenient, but makes adding tests much easier.
            try {
                Assertions.assertNotEquals(expected, new Lexer(input).lex());
            } catch (LexException ignored) {}
        }
    }

}
