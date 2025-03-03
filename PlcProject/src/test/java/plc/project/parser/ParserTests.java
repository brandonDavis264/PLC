package plc.project.parser;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.LexException;
import plc.project.lexer.Lexer;
import plc.project.lexer.Token;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the last project part for more information.
 */
final class ParserTests {

    public sealed interface Input {
        record Tokens(List<Token> tokens) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, Ast.Source expected) {
        test(input, expected, Parser::parseSource);
    }

    //Write some more tests for Source
    private static Stream<Arguments> testSource() {
        return Stream.of(
                Arguments.of("Single",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt"))
                        ))
                ),
                Arguments.of("Multiple",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "third"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Expression(new Ast.Expr.Variable("first")),
                                new Ast.Stmt.Expression(new Ast.Expr.Variable("second")),
                                new Ast.Stmt.Expression(new Ast.Expr.Variable("third"))
                        ))
                ),
                // Test for no arguments (empty source)
                Arguments.of("None",
                        new Input.Tokens(List.of()),
                        new Ast.Source(List.of()) // Empty list of statements
                ),

                Arguments.of("Single Let Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "y"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Variable("y")))
                        ))
                ),
                Arguments.of("Def Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "func"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "a"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER, "b"),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Def("func", List.of("a", "b"), List.of(
                                        new Ast.Stmt.Let("x", Optional.empty())
                                ))
                        ))
                ),
                Arguments.of("If Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "IF"),
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "y"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "ELSE"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "z"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.If(
                                        new Ast.Expr.Variable("x"),
                                        List.of(new Ast.Stmt.Let("y", Optional.empty())),
                                        List.of(new Ast.Stmt.Let("z", Optional.empty()))
                                )
                        ))
                ),
                Arguments.of("For Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "i"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "range"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.For("i", new Ast.Expr.Variable("range"), List.of(
                                        new Ast.Stmt.Let("x", Optional.empty())
                                ))
                        ))
                ),
                Arguments.of("Return Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "RETURN"),
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("x")))
                        ))
                ),
                Arguments.of("Expression Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "y"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Assignment(new Ast.Expr.Variable("x"), new Ast.Expr.Variable("y"))
                        ))
                ),
                // Test missing OBJECT keyword (should fail)
                Arguments.of("Missing OBJECT",
                        new Input.Tokens(List.of(
                                // Missing "OBJECT"
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test missing DO keyword in function (should fail)
                Arguments.of("Missing DO in Function Definition",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "myFunc"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for just a semicolon (should fail)
                Arguments.of("Just a Semicolon",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        null // Expected ParseException, no valid statement
                ),

                // Test for missing IF in an if-statement (should fail)
                Arguments.of("Missing IF in If-Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "x"), // Missing "IF"
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "y"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException, should fail due to missing "IF"
                ),

                // Test for missing FOR in loop (should fail)
                Arguments.of("Missing FOR in Loop",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "ident"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "range"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException, should fail due to missing "FOR"
                )
        );
    }


    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, Ast.Stmt.Let expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
                // Test for a valid declaration without initialization
                Arguments.of("Declaration",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Let("name", Optional.empty())
                ),

                // Test for a valid initialization with an expression
                Arguments.of("Initialization",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Let("name", Optional.of(new Ast.Expr.Variable("expr")))
                ),

                // Test for a missing semicolon, which should result in a ParseException
                Arguments.of("Missing Semicolon",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "expr")
                        )),
                        null // Expected ParseException
                ),

                Arguments.of("Declaration Missing Semicolon",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "name")
                        )),
                        null // Expected ParseException
                ),

                // Test for a missing '=' in Let statement (invalid syntax)
                Arguments.of("Missing Equals Sign",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        null // Expected ParseException
                ),

                // Test for a missing variable name in Let statement
                Arguments.of("Missing Variable Name",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        null // Expected ParseException
                ),

                // Test for a missing expression after '=' in Let statement
                Arguments.of("Missing Expression",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        null // Expected ParseException
                )
        );
}

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, Ast.Stmt.Def expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
                // Test for a basic function definition with no parameters and no body
                Arguments.of("Base",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.Def("name", List.of(), List.of())
                ),

                // Test for a function definition with one parameter
                Arguments.of("Parameter",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "parameter"),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.Def("name", List.of("parameter"), List.of())
                ),

                // Test for multiple parameters
                Arguments.of("Multiple Parameters",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "param1"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER, "param2"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER, "param3"),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.Def("name", List.of("param1", "param2", "param3"), List.of())
                ),

                // Test for a function with one statement in the body
                Arguments.of("One Statement",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.Def("name", List.of(),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt"))))
                ),

                // Test for a function with multiple statements in the body
                Arguments.of("Multiple Statements",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt1"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "stmt2"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "stmt3"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.Def("name", List.of(),
                                List.of(
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt1")),
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt2")),
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt3"))
                                ))
                ),
                Arguments.of("Missing ()",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                // Missing "DO"
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing `DO` keyword (should fail)
                Arguments.of("Missing DO",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "parameter"),
                                new Token(Token.Type.OPERATOR, ")"),
                                // Missing "DO"
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing parameter after a comma
                Arguments.of("Missing Parameter After Comma",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "param1"),
                                new Token(Token.Type.OPERATOR, ","), // Comma without a following parameter
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing function name
                Arguments.of("Missing Name",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                // Missing function name
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing END keyword
                Arguments.of("No END",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO")
                                // Missing "END"
                        )),
                        null // Expected ParseException
                )
        );
    }


    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, Ast.Stmt.If expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
                // Test for a basic if statement with a condition and a single statement in the "then" block
                Arguments.of("If",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "IF"),
                                new Token(Token.Type.IDENTIFIER, "cond"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "then"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.If(
                                new Ast.Expr.Variable("cond"),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                                List.of()
                        )
                ),

                // Test for an if-else statement
                Arguments.of("Else",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "IF"),
                                new Token(Token.Type.IDENTIFIER, "cond"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "then"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "ELSE"),
                                new Token(Token.Type.IDENTIFIER, "else"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.If(
                                new Ast.Expr.Variable("cond"),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("then"))),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("else")))
                        )
                ),

                // Test for missing "DO" keyword (should fail)
                Arguments.of("Missing DO",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "IF"),
                                new Token(Token.Type.IDENTIFIER, "cond"),
                                // Missing "DO"
                                new Token(Token.Type.IDENTIFIER, "then"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing expression in IF (should fail)
                Arguments.of("Missing Expression in IF",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "IF"),
                                // Missing condition expression
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "then"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for multiple expressions in IF and ELSE body
                Arguments.of("Multiple Expressions in IF and ELSE",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "IF"),
                                new Token(Token.Type.IDENTIFIER, "cond"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "then1"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "then2"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "ELSE"),
                                new Token(Token.Type.IDENTIFIER, "else1"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "else2"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.If(
                                new Ast.Expr.Variable("cond"),
                                List.of(
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("then1")),
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("then2"))
                                ),
                                List.of(
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("else1")),
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("else2"))
                                )
                        )
                ),

                // Test for missing END (should fail)
                Arguments.of("Missing END",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "IF"),
                                new Token(Token.Type.IDENTIFIER, "cond"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "then"),
                                new Token(Token.Type.OPERATOR, ";")
                                // Missing "END"
                        )),
                        null // Expected ParseException
                )
        );
    }


    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, Ast.Stmt.For expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
                // Test for a valid for-loop statement
                Arguments.of("For",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.For(
                                "name",
                                new Ast.Expr.Variable("expr"),
                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt")))
                        )
                ),

                // Test for missing "IN" keyword (should fail)
                Arguments.of("Missing IN",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing "FOR" keyword (should fail)
                Arguments.of("Missing FOR",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing "DO" keyword (should fail)
                Arguments.of("Missing DO",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                // Missing "DO"
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for missing "END" keyword (should fail)
                Arguments.of("Missing END",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";")
                                // Missing "END"
                        )),
                        null // Expected ParseException
                ),

                // Test for missing identifier (should fail)
                Arguments.of("Missing Identifier",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                // Missing loop variable
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test for multiple statements inside the loop body
                Arguments.of("Multiple Statements",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt1"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "stmt2"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.For(
                                "name",
                                new Ast.Expr.Variable("expr"),
                                List.of(
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt1")),
                                        new Ast.Stmt.Expression(new Ast.Expr.Variable("stmt2"))
                                )
                        )
                ),

                // Test for an empty loop body (should work)
                Arguments.of("No Statements",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Stmt.For(
                                "name",
                                new Ast.Expr.Variable("expr"),
                                List.of() // Empty body
                        )
                ),

                // Test for missing expression after "IN" (should fail)
                Arguments.of("No Expression After IN",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "FOR"),
                                new Token(Token.Type.IDENTIFIER, "name"),
                                new Token(Token.Type.IDENTIFIER, "IN"),
                                // Missing expression
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "stmt"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, Ast.Stmt.Return expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
                Arguments.of("Return",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "RETURN"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Return(Optional.empty())
                ),
                Arguments.of("Return",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "RETURN"),
                                new Token(Token.Type.IDENTIFIER, "var"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("var")))
                ),
                Arguments.of("Missing simcolon",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "RETURN")
                        )),
                        null
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, Ast.Stmt.Expression expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
                Arguments.of("Variable",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "variable"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                ),
                Arguments.of("Function",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Expression(new Ast.Expr.Function("function", List.of()))
                ),
                Arguments.of("Integer",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "1"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Expression(new Ast.Expr.Literal(new BigInteger("1")))
                ),
                Arguments.of("Missing Semicolon",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "variable")
                        )),
                        null //ParseException
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, Ast.Stmt.Assignment expected) {
        test(input, expected, Parser::parseStmt);
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
                Arguments.of("Variable",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "variable"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "value"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Assignment(
                                new Ast.Expr.Variable("variable"),
                                new Ast.Expr.Variable("value")
                        )
                ),
                Arguments.of("Property",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "object"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "property"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "value"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Assignment(
                                new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                                new Ast.Expr.Variable("value")
                        )
                ),

                Arguments.of("1=s;",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "1"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "s"),
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        new Ast.Stmt.Assignment(
                                new Ast.Expr.Literal(new BigInteger("1")),
                                new Ast.Expr.Variable("s")
                        )
                ),

                // Test for missing semicolon (should fail)
                Arguments.of("Missing Semicolon",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "variable"),
                                new Token(Token.Type.OPERATOR, "="),
                                new Token(Token.Type.IDENTIFIER, "value")
                                // Missing semicolon
                        )),
                        null // Expected ParseException
                ),

                // Test for missing expression after "=" (should fail)
                Arguments.of("Missing Expression After '='",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "variable"),
                                new Token(Token.Type.OPERATOR, "="),
                                // Missing right-hand expression
                                new Token(Token.Type.OPERATOR, ";")
                        )),
                        null // Expected ParseException
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, Ast.Expr.Literal expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
                Arguments.of("Nil",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "NIL")
                        )),
                        new Ast.Expr.Literal(null)
                ),
                Arguments.of("Boolean",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "TRUE")
                        )),
                        new Ast.Expr.Literal(true)
                ),
                Arguments.of("Integer",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "1")
                        )),
                        new Ast.Expr.Literal(new BigInteger("1"))
                ),
                Arguments.of("Exponent Positive",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "1e4")
                        )),
                        new Ast.Expr.Literal(new BigDecimal("1e4").toBigIntegerExact())
                ),
                Arguments.of("Exponent Positive",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "1e+4")
                        )),
                        new Ast.Expr.Literal(new BigDecimal("1e+4").toBigIntegerExact())
                ),
                Arguments.of("Exponent Negative",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "1e-5")
                        )),
                        new Ast.Expr.Literal(new BigDecimal("1e-5"))
                ),

                Arguments.of("Exponent Negative but Integer",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "300e-2")
                        )),
                        new Ast.Expr.Literal(new BigDecimal("300e-2").toBigIntegerExact())
                ),

                Arguments.of("Decimal Exponent Negative",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.DECIMAL, "1.0e-4")
                        )),
                        new Ast.Expr.Literal(new BigDecimal("1.0e-4"))
                ),


                Arguments.of("Decimal Exponent Positive",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.DECIMAL, "1.0e-4")
                        )),
                        new Ast.Expr.Literal(new BigDecimal("1.0e-4"))
                ),

                Arguments.of("Decimal",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.DECIMAL, "1.0")
                        )),
                        new Ast.Expr.Literal(new BigDecimal("1.0"))
                ),
                Arguments.of("Character",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.CHARACTER, "\'c\'")
                        )),
                        new Ast.Expr.Literal('c')
                ),
                Arguments.of("Character Escape",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.CHARACTER, "\'\\n\'")
                        )),
                        new Ast.Expr.Literal('\n')
                ),
                Arguments.of("String",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.STRING, "\"string\"")
                        )),
                        new Ast.Expr.Literal("string")
                ),
                Arguments.of("String Escapes",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.STRING, "\" \\b\\n\\r\\t\\'\\\"\\\\ \"")
                        )),
                        new Ast.Expr.Literal(" \b\n\r\t\'\"\\ ")
                ),
                Arguments.of("Even Escape Slash Sequnce",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.STRING, "\"\\\\\\\\\"\"")
                        )),
                        new Ast.Expr.Literal("\\\\\"")
                ),
                Arguments.of("Odd Escape Slash Sequnce",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.STRING, "\"\\\\\\\\\\\\\\nNewLine\"")
                        )),
                        new Ast.Expr.Literal("\\\\\\\nNewLine")
                ),
                Arguments.of("String Newline Escape",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.STRING, "\"Hello,\\nWorld!\"")
                        )),
                        new Ast.Expr.Literal("Hello,\nWorld!")
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, Ast.Expr.Group expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
                Arguments.of("Group",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "expr"),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Group(new Ast.Expr.Variable("expr"))
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, Ast.Expr.Binary expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
                Arguments.of("Addition",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "left"),
                                new Token(Token.Type.OPERATOR, "+"),
                                new Token(Token.Type.IDENTIFIER, "right")
                        )),
                        new Ast.Expr.Binary(
                                "+",
                                new Ast.Expr.Variable("left"),
                                new Ast.Expr.Variable("right")
                        )
                ),
                Arguments.of("Multiplication",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "left"),
                                new Token(Token.Type.OPERATOR, "*"),
                                new Token(Token.Type.IDENTIFIER, "right")
                        )),
                        new Ast.Expr.Binary(
                                "*",
                                new Ast.Expr.Variable("left"),
                                new Ast.Expr.Variable("right")
                        )
                ),
                Arguments.of("Equal Precedence",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.OPERATOR, "+"),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.OPERATOR, "+"),
                                new Token(Token.Type.IDENTIFIER, "third")
                        )),
                        new Ast.Expr.Binary(
                                "+",
                                new Ast.Expr.Binary(
                                        "+",
                                        new Ast.Expr.Variable("first"),
                                        new Ast.Expr.Variable("second")
                                ),
                                new Ast.Expr.Variable("third")
                        )
                ),
                Arguments.of("Lower Precedence",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.OPERATOR, "+"),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.OPERATOR, "*"),
                                new Token(Token.Type.IDENTIFIER, "third")
                        )),
                        new Ast.Expr.Binary(
                                "+",
                                new Ast.Expr.Variable("first"),
                                new Ast.Expr.Binary(
                                        "*",
                                        new Ast.Expr.Variable("second"),
                                        new Ast.Expr.Variable("third")
                                )
                        )
                ),
                Arguments.of("Higher Precedence",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.OPERATOR, "*"),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.OPERATOR, "+"),
                                new Token(Token.Type.IDENTIFIER, "third")
                        )),
                        new Ast.Expr.Binary(
                                "+",
                                new Ast.Expr.Binary(
                                        "*",
                                        new Ast.Expr.Variable("first"),
                                        new Ast.Expr.Variable("second")
                                ),
                                new Ast.Expr.Variable("third")
                        )
                ),
                Arguments.of("Higher Precedence with ')'",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.OPERATOR, "*"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.OPERATOR, "+"),
                                new Token(Token.Type.IDENTIFIER, "third"),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Binary(
                                "*",
                                new Ast.Expr.Variable("first"),
                                new Ast.Expr.Group(
                                        new Ast.Expr.Binary(
                                        "+",
                                        new Ast.Expr.Variable("second"),
                                        new Ast.Expr.Variable("third"))
                                )
                        )
                ),
                Arguments.of("Comparison",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.IDENTIFIER, "AND"),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.IDENTIFIER, "OR"),
                                new Token(Token.Type.IDENTIFIER, "third")
                        )),
                        new Ast.Expr.Binary(
                                "OR",
                                new Ast.Expr.Binary(
                                        "AND",
                                        new Ast.Expr.Variable("first"),
                                        new Ast.Expr.Variable("second")
                                ),
                                new Ast.Expr.Variable("third")
                        )
                ),

                //Make Test for Operator precedance with Equality Operators

                Arguments.of("Equality",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.OPERATOR, "=="),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.OPERATOR, ">="),
                                new Token(Token.Type.IDENTIFIER, "third")
                        )),
                        new Ast.Expr.Binary(
                                ">=",
                                new Ast.Expr.Binary(
                                        "==",
                                        new Ast.Expr.Variable("first"),
                                        new Ast.Expr.Variable("second")
                                ),
                                new Ast.Expr.Variable("third")
                        )
                ),
                //Make Complex Test With all expression types
                Arguments.of("Precedence",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "first"),
                                new Token(Token.Type.OPERATOR, "+"),
                                new Token(Token.Type.IDENTIFIER, "second"),
                                new Token(Token.Type.OPERATOR, "*"),
                                new Token(Token.Type.IDENTIFIER, "third"),
                                new Token(Token.Type.OPERATOR, "OR"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "Fourth"),
                                new Token(Token.Type.OPERATOR, "=="),
                                new Token(Token.Type.IDENTIFIER, "Fith"),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "AND"),
                                new Token(Token.Type.IDENTIFIER, "Sixth")
                        )),
                        new Ast.Expr.Binary(
                                "AND",
                                new Ast.Expr.Binary(
                                        "OR",
                                        new Ast.Expr.Binary(
                                                "+",
                                                new Ast.Expr.Variable("first"),
                                                new Ast.Expr.Binary(
                                                        "*",
                                                        new Ast.Expr.Variable("second"),
                                                        new Ast.Expr.Variable("third")
                                                )
                                        ),
                                         new Ast.Expr.Group(
                                            new Ast.Expr.Binary(
                                                    "==",
                                                    new Ast.Expr.Variable("Fourth"),
                                                    new Ast.Expr.Variable("Fith")
                                            )
                                         )
                                ),
                                new Ast.Expr.Variable("Sixth")
                        )
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, Ast.Expr.Variable expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
                Arguments.of("Variable",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "variable")
                        )),
                        new Ast.Expr.Variable("variable")
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, Ast.Expr.Property expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
                Arguments.of("Property",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "receiver"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "property")
                        )),
                        new Ast.Expr.Property(
                                new Ast.Expr.Variable("receiver"),
                                "property"
                        )
                ),
                Arguments.of("Multiple Embeded Properties",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "y"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "z")
                        )),
                        new Ast.Expr.Property(
                                new Ast.Expr.Property(
                                        new Ast.Expr.Variable("x"), "y"),
                                "z"
                        )
                ),
                Arguments.of("Number Property",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.INTEGER, "1"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "property")
                        )),
                        new Ast.Expr.Property(
                                new Ast.Expr.Literal(new BigInteger("1")),
                                "property"
                        )
                )

        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, Ast.Expr.Function expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
                Arguments.of("Function",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Function("function", List.of())
                ),
                Arguments.of("Argument",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "argument"),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Function("function", List.of(
                                new Ast.Expr.Variable("argument")
                        ))
                ),

                Arguments.of("Multiple Argument",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "x1"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER,"x2"),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Function("function", List.of(
                                new Ast.Expr.Variable("x1"),
                                new Ast.Expr.Variable("x2")
                        ))
                ),
                Arguments.of("Multiple Argument 3",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "x1"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER,"x2"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER,"x3"),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Function("function", List.of(
                                new Ast.Expr.Variable("x1"),
                                new Ast.Expr.Variable("x2"),
                                new Ast.Expr.Variable("x3")
                        ))
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, Ast.Expr.Method expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
                Arguments.of("Method",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "receiver"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "method"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Method(
                                new Ast.Expr.Variable("receiver"),
                                "method",
                                List.of()
                        )
                ),
                Arguments.of("Multiple Methods",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "x"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "y"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "z"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Method(
                                new Ast.Expr.Method(
                                        new Ast.Expr.Variable("x"),
                                        "y",
                                        List.of()),
                                "z",
                                List.of()
                        )
                ),
                Arguments.of("Multiple Argument",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "reciever"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "x1"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER, "x2"),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Method(
                                new Ast.Expr.Variable("reciever"),
                                "function",
                                List.of(
                                    new Ast.Expr.Variable("x1"),
                                    new Ast.Expr.Variable("x2")
                        ))
                ),
                Arguments.of("Multiple Argument 3",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "reciever"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "x1"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER, "x2"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.IDENTIFIER, "x3"),
                                new Token(Token.Type.OPERATOR, ")")
                        )),
                        new Ast.Expr.Method(
                                new Ast.Expr.Variable("reciever"),
                                "function",
                                List.of(
                                        new Ast.Expr.Variable("x1"),
                                        new Ast.Expr.Variable("x2"),
                                        new Ast.Expr.Variable("x3")
                                ))
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, Ast.Expr.ObjectExpr expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
                // Test for an object expression with a single field
                Arguments.of("Field",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(new Ast.Stmt.Let("field", Optional.empty())),
                                List.of()
                        )
                ),

                // Test for an object expression with a single method
                Arguments.of("Method",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "method"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(),
                                List.of(new Ast.Stmt.Def("method", List.of(), List.of()))
                        )
                ),

                // Test multiple let and def statements together
                Arguments.of("Multiple Let and Def",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field1"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field2"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "method1"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END"),
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "method2"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(
                                        new Ast.Stmt.Let("field1", Optional.empty()),
                                        new Ast.Stmt.Let("field2", Optional.empty())
                                ),
                                List.of(
                                        new Ast.Stmt.Def("method1", List.of(), List.of()),
                                        new Ast.Stmt.Def("method2", List.of(), List.of())
                                )
                        )
                ),


                // Test a single def and let statement together
                Arguments.of("Single Let and Def",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "method"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(new Ast.Stmt.Let("field", Optional.empty())),
                                List.of(new Ast.Stmt.Def("method", List.of(), List.of()))
                        )
                ),

                // Test multiple def statements, no let
                Arguments.of("Multiple Def, No Let",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "method1"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END"),
                                new Token(Token.Type.IDENTIFIER, "DEF"),
                                new Token(Token.Type.IDENTIFIER, "method2"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.OPERATOR, ")"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(),
                                List.of(
                                        new Ast.Stmt.Def("method1", List.of(), List.of()),
                                        new Ast.Stmt.Def("method2", List.of(), List.of())
                                )
                        )
                ),

                // Test multiple let statements, no def
                Arguments.of("Multiple Let, No Def",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field1"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field2"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(
                                        new Ast.Stmt.Let("field1", Optional.empty()),
                                        new Ast.Stmt.Let("field2", Optional.empty())
                                ),
                                List.of()
                        )
                ),

                // Test missing OBJECT keyword (should fail) ?
                //Returns: Variable[name=DO]
                //Test in parse Sourse since this would be an incorrect statement

                // Test missing DO keyword (should fail)
                Arguments.of("Missing DO",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                // Missing "DO"
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        null // Expected ParseException
                ),

                // Test missing END keyword (should fail)
                Arguments.of("Missing END",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field"),
                                new Token(Token.Type.OPERATOR, ";")
                                // Missing "END"
                        )),
                        null // Expected ParseException
                ),

                // Test object expression with identifier
                Arguments.of("Object Expression with Identifier",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "parent"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "LET"),
                                new Token(Token.Type.IDENTIFIER, "field"),
                                new Token(Token.Type.OPERATOR, ";"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.of("parent"),
                                List.of(new Ast.Stmt.Let("field", Optional.empty())),
                                List.of()
                        )
                ),

                Arguments.of("Object do end",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(),
                                List.of()
                        )
                ),

                Arguments.of("Object ident do end",
                        new Input.Tokens(List.of(
                                new Token(Token.Type.IDENTIFIER, "OBJECT"),
                                new Token(Token.Type.IDENTIFIER, "ident"),
                                new Token(Token.Type.IDENTIFIER, "DO"),
                                new Token(Token.Type.IDENTIFIER, "END")
                        )),
                        new Ast.Expr.ObjectExpr(
                                Optional.of("ident"),
                                List.of(),
                                List.of()
                        )
                )
                //Test no def or let statements, i.e. empty object expression
        );
    }


    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, Ast.Source expected) {
        test(input, expected, Parser::parseSource);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
                // Test "Hello World" program with a simple DEF statement
                Arguments.of("Hello World",
                        new Input.Program("""
                    DEF main() DO
                        print("Hello, World!");
                    END
                    """),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Def("main", List.of(), List.of(
                                        new Ast.Stmt.Expression(new Ast.Expr.Function(
                                                "print",
                                                List.of(new Ast.Expr.Literal("Hello, World!"))
                                        ))
                                ))
                        ))
                ),

                // Test a program with multiple statement types
                Arguments.of("Multiple Statements",
                        new Input.Program("""
                    LET x = 10;
                    DEF factorial(n , k, r) DO
                        LET result = 1;
                        FOR i IN x DO
                            result = result * i;
                        END
                        RETURN result;
                    END
                    """),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Let("x", Optional.of(new Ast.Expr.Literal(new BigInteger("10")))),
                                new Ast.Stmt.Def("factorial", List.of("n", "k", "r"), List.of(
                                        new Ast.Stmt.Let("result", Optional.of(new Ast.Expr.Literal(new BigInteger("1")))),
                                        new Ast.Stmt.For("i", new Ast.Expr.Variable("x"), List.of(
                                                new Ast.Stmt.Assignment(
                                                        new Ast.Expr.Variable("result"),
                                                        new Ast.Expr.Binary("*", new Ast.Expr.Variable("result"), new Ast.Expr.Variable("i"))
                                                )
                                        )),
                                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("result")))
                                ))
                        ))
                ),

                // Test an if-else statement inside a function
                Arguments.of("If Else Statement",
                        new Input.Program("""
                    DEF checkEvenOdd(n) DO
                        IF n + 1 == 2 DO
                            print("Even");
                        ELSE
                            print("Odd");
                        END
                    END
                    """),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Def("checkEvenOdd", List.of("n"), List.of(
                                        new Ast.Stmt.If(
                                                new Ast.Expr.Binary("==",
                                                        new Ast.Expr.Binary("+", new Ast.Expr.Variable("n"), new Ast.Expr.Literal(new BigInteger("1"))),
                                                        new Ast.Expr.Literal(new BigInteger("2"))
                                                ),
                                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("print", List.of(new Ast.Expr.Literal("Even"))))),
                                                List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("print", List.of(new Ast.Expr.Literal("Odd")))))
                                        )
                                ))
                        ))
                ),

                // Test object definition with fields and methods, as an expression
                Arguments.of("Object Definition",
                        new Input.Program("""
                    OBJECT MyObject DO
                        LET field1;
                        DEF method1() DO
                            print("Method 1");
                        END
                    END;
                    """),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Expression(new Ast.Expr.ObjectExpr(Optional.of("MyObject"), List.of(
                                        new Ast.Stmt.Let("field1", Optional.empty())
                                ), List.of(
                                        new Ast.Stmt.Def("method1", List.of(), List.of(
                                                new Ast.Stmt.Expression(new Ast.Expr.Function("print", List.of(new Ast.Expr.Literal("Method 1"))))
                                        ))
                                )))
                        ))
                ),

                // Test a return statement with an expression
                Arguments.of("Return Statement",
                        new Input.Program("""
                    DEF double(x) DO
                        RETURN x * 2;
                    END
                    """),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Def("double", List.of("x"), List.of(
                                        new Ast.Stmt.Return(Optional.of(
                                                new Ast.Expr.Binary("*", new Ast.Expr.Variable("x"), new Ast.Expr.Literal(new BigInteger("2")))
                                        ))
                                ))
                        ))
                ),

                // Test a program with a combination of different expressions
                Arguments.of("Complex Expressions",
                        new Input.Program("""
                    DEF complex() DO
                        LET a = 5;
                        LET b = a * 2;
                        IF b > 5 DO
                            RETURN b + a;
                        ELSE
                            RETURN a - b;
                        END
                    END
                    """),
                        new Ast.Source(List.of(
                                new Ast.Stmt.Def("complex", List.of(), List.of(
                                        new Ast.Stmt.Let("a", Optional.of(new Ast.Expr.Literal(new BigInteger("5")))),
                                        new Ast.Stmt.Let("b", Optional.of(
                                                new Ast.Expr.Binary("*", new Ast.Expr.Variable("a"), new Ast.Expr.Literal(new BigInteger("2")))
                                        )),
                                        new Ast.Stmt.If(
                                                new Ast.Expr.Binary(">", new Ast.Expr.Variable("b"), new Ast.Expr.Literal(new BigInteger("5"))),
                                                List.of(new Ast.Stmt.Return(Optional.of(
                                                        new Ast.Expr.Binary("+", new Ast.Expr.Variable("b"), new Ast.Expr.Variable("a"))
                                                ))),
                                                List.of(new Ast.Stmt.Return(Optional.of(
                                                        new Ast.Expr.Binary("-", new Ast.Expr.Variable("a"), new Ast.Expr.Variable("b"))
                                                )))
                                        )
                                ))
                        ))
                )
        );
    }



    @ParameterizedTest
    @MethodSource
    void testException(String test, List<Token> input) {
        Assertions.assertThrows(ParseException.class, () -> new Parser(input).parseExpr());
    }

    public static Stream<Arguments> testException() {
        return Stream.of(
                Arguments.of("Missing a '(': ",
                        List.of(
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "(")
                        )
                ),
                Arguments.of("Missing ',': ",
                        List.of(
                                new Token(Token.Type.IDENTIFIER, "reciever"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "x1"),
                                new Token(Token.Type.OPERATOR, " "),
                                new Token(Token.Type.IDENTIFIER, "x2"),
                                new Token(Token.Type.OPERATOR, ")")
                        )
                ),
                Arguments.of("Empty Token: ",
                        List.of(
                                new Token(Token.Type.IDENTIFIER, "reciever"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "x1"),
                                new Token(Token.Type.OPERATOR, ",")
                        )
                ),
                Arguments.of("Invalid Token: ",
                        List.of(
                                new Token(Token.Type.OPERATOR, "#")
                        )
                ),
                Arguments.of("Missing argument: ",
                        List.of(
                                new Token(Token.Type.IDENTIFIER, "reciever"),
                                new Token(Token.Type.OPERATOR, "."),
                                new Token(Token.Type.IDENTIFIER, "function"),
                                new Token(Token.Type.OPERATOR, "("),
                                new Token(Token.Type.IDENTIFIER, "x1"),
                                new Token(Token.Type.OPERATOR, ","),
                                new Token(Token.Type.OPERATOR, ")")
                        )
                )
        );
    }



    interface ParserMethod<T> {
        T invoke(Parser parser) throws ParseException;
    }

    /**
     * Test function for the Parser. The {@link Input} parameter handles parser
     * input, which may either be a direct list of tokens or a String program.
     * Using a String program is easier for tests, but relies on your Lexer
     * working properly!
     */
    private static <T extends Ast> void test(Input input, @Nullable T expected, ParserMethod<T> method) {
        var tokens = switch (input) {
            case Input.Tokens i -> i.tokens();
            case Input.Program i -> Assertions.assertDoesNotThrow(() -> new Lexer(i.program).lex());
        };
        Parser parser = new Parser(tokens);
        if (expected != null) {
            var ast = Assertions.assertDoesNotThrow(() -> method.invoke(parser));
            Assertions.assertEquals(expected, ast);
        } else {
            Assertions.assertThrows(ParseException.class, () -> method.invoke(parser));
        }
    }

}
