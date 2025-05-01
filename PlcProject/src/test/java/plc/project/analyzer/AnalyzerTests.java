package plc.project.analyzer;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import plc.project.lexer.Lexer;
import plc.project.parser.Ast;
import plc.project.parser.ParseException;
import plc.project.parser.Parser;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class AnalyzerTests {

    public sealed interface Input {
        record Ast(plc.project.parser.Ast ast) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Literal("value"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Literal("value", Type.STRING))
                ))
            ),
            Arguments.of("Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("functionAny", List.of(new Ast.Expr.Literal("value"))))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Expression(new Ir.Expr.Function("functionAny", List.of(new Ir.Expr.Literal("value", Type.STRING)), Type.ANY))
                ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.ANY, Optional.empty()),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.ANY))
                ))
            ),
            Arguments.of("Declaration Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("String"), Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.empty()),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.STRING))
                ))
            ),
            Arguments.of("Initialization",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty(), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.STRING))
                ))
            ),
            Arguments.of("Initialization Type Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Comparable"), Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("name"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Let("name", Type.COMPARABLE, Optional.of(new Ir.Expr.Literal("value", Type.STRING))),
                    new Ir.Stmt.Expression(new Ir.Expr.Variable("name", Type.COMPARABLE))
                ))
            ),
            Arguments.of("Initialization Type Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of("Comparable"), Optional.of(new Ast.Expr.Literal(null)))
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Redefined",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty())
                ))),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.empty(), List.of()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.ANY, List.of()),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.ANY))
                ))
            ),
            Arguments.of("Parameter Type",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(Optional.of("String")), Optional.empty(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Variable("parameter"))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(new Ir.Stmt.Def.Parameter("parameter", Type.STRING)), Type.ANY, List.of(
                        new Ir.Stmt.Expression(new Ir.Expr.Variable("parameter", Type.STRING))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(new Ir.Expr.Literal("argument", Type.STRING)), Type.ANY))
                ))
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Def.
            Arguments.of("Return Type/Value",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.STRING, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.STRING))
                ))
            ),
            Arguments.of("Undefined Property Type",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Def(
                                    "name",
                                    List.of("property"),
                                    List.of(Optional.of("Undefined")),
                                    Optional.empty(),
                                    List.of()
                            )
                    ))),
                    null // AnalyzeException (Undefined type for property)
            ),
            Arguments.of("Undefined Return Type",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Def(
                                    "name",
                                    List.of(),
                                    List.of(),
                                    Optional.of("Undefined"),
                                    List.of()
                            )
                    ))),
                    null // AnalyzeException (Undefined return type for function)
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("If",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal(true), List.of(), List.of())
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(new Ir.Expr.Literal(true, Type.BOOLEAN), List.of(), List.of())
                ))
            ),
            Arguments.of("Condition Type Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(new Ast.Expr.Literal("true"), List.of(), List.of())
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Scope",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Let("name", Optional.empty())),
                        List.of(new Ast.Stmt.Let("name", Optional.empty()))
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.If(
                        new Ir.Expr.Literal(false, Type.BOOLEAN),
                        List.of(new Ir.Stmt.Let("name", Type.ANY, Optional.empty())),
                        List.of(new Ir.Stmt.Let("name", Type.ANY, Optional.empty()))
                    )
                ))
            ),
            Arguments.of("Else",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.If(
                                    new Ast.Expr.Literal(true),
                                    List.of(),
                                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Literal("else")))
                            )
                    ))),
                    new Ir.Source(List.of(
                            new Ir.Stmt.If(
                                    new Ir.Expr.Literal(true, Type.BOOLEAN),
                                    List.of(),
                                    List.of(new Ir.Stmt.Expression(new Ir.Expr.Literal("else", Type.STRING)))
                            )
                    ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("range", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("5"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("element")))
                    )
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.For(
                        "element",
                        Type.INTEGER,
                        new Ir.Expr.Function("range", List.of(
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER)
                        ), Type.ITERABLE),
                        List.of(new Ir.Stmt.Expression(new Ir.Expr.Variable("element", Type.INTEGER)))
                    )
                ))
            ),
            Arguments.of("Invalid Expression Type",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.For(
                                    "name",
                                    new Ast.Expr.Literal(new BigInteger("1")), // Invalid type (expected iterable or range)
                                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Variable("name")))
                            )
                    ))),
                    null // AnalyzeException due to invalid expression type (expected iterable or range, got Integer)
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Part of the spec for Def, but duplicated here for clarity.
            Arguments.of("Inside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("name", List.of(), Type.STRING, List.of(
                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                    )),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.STRING))
                ))
            ),
            Arguments.of("Outside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Invalid Value Type",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                                    new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal(new BigInteger("1"))))
                            )),
                            new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                    ))),
                    null // AnalyzeException due to type mismatch (expected String, got Integer)
            ),
            //Create Tesets for:
                //Return Subtype (2): DEF name() DO RETURN; END
                    // Unexpected exception (java.util.NoSuchElementException: No value present)
                Arguments.of("Return Subtype",
                        new Input.Ast(new Ast.Source(List.of(
                                new Ast.Stmt.Def("name", List.of(), List.of(), Optional.empty(), List.of(
                                        new Ast.Stmt.Return(Optional.empty())
                                )),
                                new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                        ))),
                        new Ir.Source(List.of(
                                new Ir.Stmt.Def("name", List.of(), Type.ANY, List.of(
                                        new Ir.Stmt.Return(Optional.empty())
                                )),
                                new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.ANY))
                        ))

                ),
                //Return Non-Subtype (2): DEF name(): String DO RETURN; END
                    // Unexpected exception (java.util.NoSuchElementException: No value present)
                Arguments.of("Return Non-Subtype",
                        new Input.Ast(new Ast.Source(List.of(
                                new Ast.Stmt.Def("name", List.of(), List.of(), Optional.of("String"), List.of(
                                        new Ast.Stmt.Return(Optional.empty())
                                )),
                                new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                        ))),
                        null
                ),
                //Value Subtype (2): DEF name() DO RETURN "value"; END
                    // Unexpected AnalyzeException
                    //`Type Primitive[name=Any, jvmName=Object] is not a subtype of Primitive[name=String, jvmName=String]` thrown, expected
                        // IR `Def[name=name, parameters=[], returns=Primitive[name=Any, jvmName=Object],
                            // body=[Return[value=Optional[Literal[value=value, type=Primitive[name=String, jvmName=String]]]]]]`.
                Arguments.of("Value Subtype",
                        new Input.Ast(new Ast.Source(List.of(
                                new Ast.Stmt.Def("name", List.of(), List.of(), Optional.empty(), List.of(
                                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                                )),
                                new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                        ))),
                        new Ir.Source(List.of(
                                new Ir.Stmt.Def("name", List.of(), Type.ANY, List.of(
                                        new Ir.Stmt.Return(Optional.of(new Ir.Expr.Literal("value", Type.STRING)))
                                )),
                                new Ir.Stmt.Expression(new Ir.Expr.Function("name", List.of(), Type.ANY))
                        ))

                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Literal",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Expression(new Ast.Expr.Literal("literal"))
                    ))),
                    new Ir.Source(List.of(
                            new Ir.Stmt.Expression(new Ir.Expr.Literal("literal", Type.STRING))
                    ))
            ),
            Arguments.of("Variable",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                    ))),
                    new Ir.Source(List.of(
                            new Ir.Stmt.Expression(new Ir.Expr.Variable("variable", Type.STRING))
                    ))
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Literal",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Literal("literal"), new Ast.Expr.Literal("value"))
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Variable Type Subtype",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal("value"))
                ))),
                new Ir.Source(List.of(
                    new Ir.Stmt.Assignment.Variable(new Ir.Expr.Variable("variable", Type.STRING), new Ir.Expr.Literal("value", Type.STRING))
                ))
            ),
            Arguments.of("Variable Type Invalid",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(new Ast.Expr.Variable("variable"), new Ast.Expr.Literal(null))
                ))),
                null //AnalyzeException
            ),
            Arguments.of("Property",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Assignment(
                                    new Ast.Expr.Property(
                                            new Ast.Expr.Variable("object"),
                                            "property"
                                    ),
                                    new Ast.Expr.Literal("value")
                            )
                    ))),
                    new Ir.Source(List.of(
                            new Ir.Stmt.Assignment.Property(
                                    new Ir.Expr.Property(
                                            new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                                            "property",
                                            Type.STRING
                                    ),
                                    new Ir.Expr.Literal("value", Type.STRING)
                            )
                    ))
            )

            );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Input.Ast(
                    new Ast.Expr.Literal(true)
                ),
                new Ir.Expr.Literal(true, Type.BOOLEAN)
            ),
            Arguments.of("Integer",
                new Input.Ast(
                    new Ast.Expr.Literal(new BigInteger("1"))
                ),
                new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER)
            ),
            Arguments.of("String",
                new Input.Ast(
                    new Ast.Expr.Literal("string")
                ),
                new Ir.Expr.Literal("string", Type.STRING)
            ),
            Arguments.of("NIL",
                    new Input.Ast(
                            new Ast.Expr.Literal(null)
                    ),
                    new Ir.Expr.Literal(null, Type.NIL)
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Ast(
                    new Ast.Expr.Group(new Ast.Expr.Literal("expr"))
                ),
                new Ir.Expr.Group(new Ir.Expr.Literal("expr", Type.STRING))
            ),
            Arguments.of("Nested",
                    new Input.Ast(
                            new Ast.Expr.Group(
                                    new Ast.Expr.Group(
                                            new Ast.Expr.Group(
                                                    new Ast.Expr.Literal("expr")
                                            )
                                    )
                            )
                    ),
                    new Ir.Expr.Group(
                            new Ir.Expr.Group(
                                    new Ir.Expr.Group(
                                            new Ir.Expr.Literal("expr", Type.STRING)
                                    )
                            )
                    )
            )

            );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                    Type.INTEGER
                )
            ),
            Arguments.of("Op+ Invalid Right",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigDecimal("1.0"))
                    )
                ),
                null //AnalyzeException
            ),
            Arguments.of("Op+ String Right",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal("right")
                    )
                ),
                new Ir.Expr.Binary(
                    "+",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal("right", Type.STRING),
                    Type.STRING
                )
            ),
            Arguments.of("Op< Integer",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new Ir.Expr.Binary(
                    "<",
                    new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                    new Ir.Expr.Literal(new BigInteger("2"), Type.INTEGER),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("Op< Right Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(null)
                    )
                ),
                null //AnalyzeException
            ),
            Arguments.of("OpAND Boolean",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "AND",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(false)
                    )
                ),
                new Ir.Expr.Binary(
                    "AND",
                    new Ir.Expr.Literal(true, Type.BOOLEAN),
                    new Ir.Expr.Literal(false, Type.BOOLEAN),
                    Type.BOOLEAN
                )
            ),
            Arguments.of("OpOR Right Invalid",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "OR",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(null)
                    )
                ),
                null //AnalyzeException
            ),
            Arguments.of("Op== Equatable Left",
                    new Input.Ast(
                            new Ast.Expr.Binary(
                                    "==",
                                    new Ast.Expr.Variable("equatable"),
                                    new Ast.Expr.Variable("equatable")
                            )
                    ),
                    new Ir.Expr.Binary(
                            "==",
                            new Ir.Expr.Variable("equatable", Type.EQUATABLE),  // Assuming Type.EQUATABLE exists
                            new Ir.Expr.Variable("equatable", Type.EQUATABLE),
                            Type.BOOLEAN
                    )
            ),

            Arguments.of("Op== Equatable Subtype Left",
                    new Input.Ast(
                            new Ast.Expr.Binary(
                                    "==",
                                    new Ast.Expr.Literal(new BigInteger("1")),
                                    new Ast.Expr.Variable("equatable")
                            )
                    ),
                    new Ir.Expr.Binary(
                            "==",
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Variable("equatable", Type.EQUATABLE),
                            Type.BOOLEAN
                    )
            ),

            Arguments.of("Op== Equatable Subtype Left",
                    new Input.Ast(
                            new Ast.Expr.Binary(
                                    "==",
                                    new Ast.Expr.Variable("equatable"),
                                    new Ast.Expr.Literal(new BigInteger("1"))
                            )
                    ),
                    new Ir.Expr.Binary(
                            "==",
                            new Ir.Expr.Variable("equatable", Type.EQUATABLE),
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            Type.BOOLEAN
                    )
            ),
            Arguments.of("Op== Invalid Left Type",
                    new Input.Ast(
                            new Ast.Expr.Binary(
                                    "==",
                                    new Ast.Expr.Variable("object"),
                                    new Ast.Expr.Variable("equatable")
                            )
                    ),
                    null // AnalyzeException (Invalid left type: object cannot be equated with equatable)
            ),

            Arguments.of("Op== Different Equatable Right",
                    new Input.Ast(
                            new Ast.Expr.Binary(
                                    "==",
                                    new Ast.Expr.Literal(new BigInteger("1")),
                                    new Ast.Expr.Literal(new BigDecimal("2.0"))
                            )
                    ),
                    new Ir.Expr.Binary(
                            "==",
                            new Ir.Expr.Literal(new BigInteger("1"), Type.INTEGER),
                            new Ir.Expr.Literal(new BigDecimal("2.0"), Type.DECIMAL),
                            Type.BOOLEAN
                    )
            ),

            Arguments.of("Op== Invalid Right Type",
                    new Input.Ast(
                            new Ast.Expr.Binary(
                                    "==",
                                    new Ast.Expr.Variable("equatable"),
                                    new Ast.Expr.Variable("object")
                            )
                    ),
                    null // AnalyzeException (Invalid right type: object cannot be equated with equatable)
            ),
            //Comparable Left (0/1): comparable <= 1
                //Expected an AnalyzeException to be thrown, received
                    //`Binary[operator=<=, left=Variable[name=comparable, type=Primitive[name=Comparable, jvmName=Comparable]],
                        // right=Literal[value=1, type=Primitive[name=Integer, jvmName=BigInteger]],
                        // type=Primitive[name=Boolean, jvmName=boolean]]`.
                Arguments.of("Comparable Left",
                        new Input.Ast(
                                new Ast.Expr.Binary(
                                        "<=",
                                        new Ast.Expr.Variable("comparable"),
                                        new Ast.Expr.Literal(new BigInteger("1"))
                                )
                        ),
                        null
                )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(
                    new Ast.Expr.Variable("variable")
                ),
                new Ir.Expr.Variable("variable", Type.STRING)
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Variable("undefined")
                ),
                null //AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testPropertyExpr() {
        return Stream.of(
            Arguments.of("Property",
                new Input.Ast(
                    new Ast.Expr.Property(
                        new Ast.Expr.Variable("object"),
                        "property"
                    )
                ),
                new Ir.Expr.Property(
                    new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                    "property",
                    Type.STRING
                )
            ),
            Arguments.of("Invalid Receiver Type",
                    new Input.Ast(
                            new Ast.Expr.Property(
                                    new Ast.Expr.Variable("any"),
                                    "property"
                            )
                    ),
                    null // Should throw AnalyzeException
            ),

            Arguments.of("Undefined Property",
                    new Input.Ast(
                            new Ast.Expr.Property(
                                    new Ast.Expr.Variable("object"),
                                    "undefined"
                            )
                    ),
                    null // Should throw AnalyzeException
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Ast(
                    new Ast.Expr.Function("function", List.of())
                ),
                new Ir.Expr.Function("function", List.of(), Type.NIL)
            ),
            Arguments.of("Argument",
                new Input.Ast(
                    new Ast.Expr.Function("functionAny", List.of(new Ast.Expr.Literal("argument")))
                ),
                new Ir.Expr.Function("functionAny", List.of(new Ir.Expr.Literal("argument", Type.STRING)), Type.ANY)
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Function("undefined", List.of())
                ),
                null //AnalyzeException
            ),
            Arguments.of("Invalid Argument Type",
                    new Input.Ast(
                            new Ast.Expr.Function("functionString", List.of(new Ast.Expr.Literal(new BigInteger("1"))))
                    ),
                    null // AnalyzeException expected due to argument type mismatch
            ),
            Arguments.of("Missing Argument",
                    new Input.Ast(
                            new Ast.Expr.Function("functionAny", List.of())
                    ),
                    null
            ),
            Arguments.of("Extraneous Argument",
                    new Input.Ast(
                            new Ast.Expr.Function("functionAny", List.of(
                                    new Ast.Expr.Literal(new BigInteger("1")),
                                    new Ast.Expr.Literal(new BigInteger("2"))
                            ))
                    ),
                    null
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
                Arguments.of("Method",
                        new Input.Ast(
                                new Ast.Expr.Method(
                                        new Ast.Expr.Variable("object"),
                                        "methodAny",
                                        List.of(new Ast.Expr.Literal("argument"))
                                )
                        ),
                        new Ir.Expr.Method(
                                new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                                "methodAny",
                                List.of(new Ir.Expr.Literal("argument", Type.STRING)),
                                Type.ANY
                        )
                ),

                Arguments.of("Invalid Receiver",
                        new Input.Ast(
                                new Ast.Expr.Method(
                                        new Ast.Expr.Literal(new BigInteger("1")),
                                        "method",
                                        List.of()
                                )
                        ),
                        null // should throw AnalyzeException (e.g., requireSubtype fails)
                ),

                Arguments.of("Argument",
                        new Input.Ast(
                                new Ast.Expr.Method(
                                        new Ast.Expr.Variable("object"),
                                        "methodAny",
                                        List.of(new Ast.Expr.Literal("argument"))
                                )
                        ),
                        new Ir.Expr.Method(
                                new Ir.Expr.Variable("object", Environment.scope().get("object", true).get()),
                                "methodAny",
                                List.of(new Ir.Expr.Literal("argument", Type.STRING)),
                                Type.ANY
                        )
                ),
                Arguments.of("Missing Argument",
                        new Input.Ast(
                                new Ast.Expr.Method(
                                        new Ast.Expr.Variable("object"),
                                        "methodAny",
                                        List.of()
                                )
                        ),
                        null
                ),
                Arguments.of("Extraneous Argument",
                        new Input.Ast(
                                new Ast.Expr.Method(
                                        new Ast.Expr.Variable("object"),
                                        "method",
                                        List.of(new Ast.Expr.Literal("any"))
                                )
                        ),
                        null
                )
        );

    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseExpr);
    }

    private static Stream<Arguments> testObjectExpr() {
        Function<Map<String, Type>, Type.Object> createObjectType = types -> {
            var type = new Type.Object(new Scope(null));
            types.forEach(type.scope()::define);
            return type;
        };
        return Stream.of(
                Arguments.of("Empty",
                        new Input.Ast(
                                new Ast.Expr.ObjectExpr(
                                        Optional.empty(),
                                        List.of(),
                                        List.of()
                                )
                        ),
                        new Ir.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(),
                                List.of(),
                                createObjectType.apply(Map.of())
                        )
                ),

                Arguments.of("Field",
                        new Input.Ast(
                                new Ast.Expr.Property(
                                        new Ast.Expr.ObjectExpr(
                                                Optional.empty(),
                                                List.of(new Ast.Stmt.Let("field", Optional.of(new Ast.Expr.Literal("value")))),
                                                List.of()
                                        ),
                                        "field"
                                )
                        ),
                        new Ir.Expr.Property(
                                new Ir.Expr.ObjectExpr(
                                        Optional.empty(),
                                        List.of(new Ir.Stmt.Let("field", Type.STRING, Optional.of(new Ir.Expr.Literal("value", Type.STRING)))),
                                        List.of(),
                                        createObjectType.apply(Map.of("field", Type.STRING))
                                ),
                                "field",
                                Type.STRING
                        )
                ),

                Arguments.of("Method",
                        new Input.Ast(
                                new Ast.Expr.Method(
                                        new Ast.Expr.ObjectExpr(
                                                Optional.empty(),
                                                List.of(),
                                                List.of(new Ast.Stmt.Def(
                                                        "method",
                                                        List.of(),
                                                        List.of()
                                                ))
                                        ),
                                        "method",
                                        List.of()
                                )
                        ),
                        new Ir.Expr.Method(
                                new Ir.Expr.ObjectExpr(
                                        Optional.empty(),
                                        List.of(),
                                        List.of(new Ir.Stmt.Def("method", List.of(), Type.ANY, List.of())),
                                        createObjectType.apply(Map.of("method", new Type.Function(List.of(), Type.ANY)))
                                ),
                                "method",
                                List.of(),
                                Type.ANY
                        )
                ),

                Arguments.of("Duplicate Field",
                        new Input.Ast(
                                new Ast.Expr.ObjectExpr(
                                        Optional.empty(),
                                        List.of(
                                                new Ast.Stmt.Let("name", Optional.empty()),
                                                new Ast.Stmt.Let("name", Optional.empty())
                                        ),
                                        List.of()
                                )
                        ),
                        null // Expect AnalyzeException
                ),

                Arguments.of("Method This",
                        new Input.Program("""
                            OBJECT DO
                                LET name;
                                DEF method() DO this.name; END
                            END.method()
                        """),
                        new Ir.Expr.Method(
                                new Ir.Expr.ObjectExpr(
                                        Optional.empty(),
                                        List.of(new Ir.Stmt.Let("name", Type.ANY, Optional.empty())),
                                        List.of(new Ir.Stmt.Def(
                                                "method",
                                                List.of(),
                                                Type.ANY,
                                                List.of(new Ir.Stmt.Expression(
                                                        new Ir.Expr.Property(
                                                                new Ir.Expr.Variable("this", createObjectType.apply(Map.of(
                                                                        "name", Type.ANY,
                                                                        "method", new Type.Function(List.of(), Type.ANY)
                                                                ))),
                                                                "name",
                                                                Type.ANY
                                                        )
                                                ))
                                        )),
                                        createObjectType.apply(Map.of(
                                                "name", Type.ANY,
                                                "method", new Type.Function(List.of(), Type.ANY)
                                        ))
                                ),
                                "method",
                                List.of(),
                                Type.ANY
                        )
                ),
                Arguments.of("Forward Declaration (Bonus)",
                        new Input.Program("""
                            OBJECT DO
                                DEF first() DO this.second(); END
                                DEF second() DO END
                            END
                        """),
                        new Ir.Expr.ObjectExpr(
                                Optional.empty(),
                                List.of(),
                                List.of(
                                        new Ir.Stmt.Def(
                                                "first",
                                                List.of(),
                                                Type.ANY,
                                                List.of(new Ir.Stmt.Expression(
                                                        new Ir.Expr.Method(
                                                                new Ir.Expr.Variable("this", createObjectType.apply(Map.of(
                                                                        "first", new Type.Function(List.of(), Type.ANY),
                                                                        "second", new Type.Function(List.of(), Type.ANY)
                                                                ))),
                                                                "second",
                                                                List.of(),
                                                                Type.ANY
                                                        )
                                                ))
                                        ),
                                        new Ir.Stmt.Def(
                                                "second",
                                                List.of(),
                                                Type.ANY,
                                                List.of()
                                        )
                                ),
                                createObjectType.apply(Map.of(
                                        "first", new Type.Function(List.of(), Type.ANY),
                                        "second", new Type.Function(List.of(), Type.ANY)
                                ))
                        )
                ),

                Arguments.of("Direct Field Access",
                        new Input.Program("""
                            OBJECT DO
                                LET field;
                                DEF name() DO field; END
                            END
                        """),
                        null // Expect AnalyzeException
                )

        );

    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, @Nullable Ir expected) {
        test(input, expected, Parser::parseSource);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
                Arguments.of("No Scope Usage",
                    new Input.Program("""
                        OBJECT Name DO END;
                        Name;
                    """),
                    null // Expect AnalyzeException
            ),
            Arguments.of("Hello World",
                //Input.Program makes tests *significantly* easier, but relies
                //on your Lexer and Parser being implemented correctly!
                new Input.Program("""
                    DEF main() DO
                        print("Hello, World!");
                    END
                    main();
                    """),
                new Ir.Source(List.of(
                    new Ir.Stmt.Def("main", List.of(), Type.ANY, List.of(
                        new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Hello, World!", Type.STRING)), Type.NIL)
                    ))),
                    new Ir.Stmt.Expression(new Ir.Expr.Function("main", List.of(), Type.ANY))
                ))
            ),
            Arguments.of("Fizzbuzz 5",
                    new Input.Program("""
                    LET number = 5;
                    LET mod3 = number / 3 * 3 == number;
                    LET mod5 = number / 5 * 5 == number;
                    IF mod3 AND mod5 DO
                        print("FizzBuzz");
                    ELSE 
                        IF mod3 DO
                            print("Fizz");
                        ELSE   
                            IF mod5 DO
                                print("Buzz");
                            ELSE
                                print(number);
                            END
                        END
                    END
                """),
                new Ir.Source(List.of(
                        new Ir.Stmt.Let("number", Type.INTEGER, Optional.of(new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER))),

                        new Ir.Stmt.Let("mod3", Type.BOOLEAN, Optional.of(
                                new Ir.Expr.Binary("==",
                                        new Ir.Expr.Binary("*",
                                                new Ir.Expr.Binary("/",
                                                        new Ir.Expr.Variable("number", Type.INTEGER),
                                                        new Ir.Expr.Literal(new BigInteger("3"), Type.INTEGER),
                                                        Type.INTEGER
                                                ),
                                                new Ir.Expr.Literal(new BigInteger("3"), Type.INTEGER),
                                                Type.INTEGER
                                        ),
                                        new Ir.Expr.Variable("number", Type.INTEGER),
                                        Type.BOOLEAN
                                )
                        )),

                        new Ir.Stmt.Let("mod5", Type.BOOLEAN, Optional.of(
                                new Ir.Expr.Binary("==",
                                        new Ir.Expr.Binary("*",
                                                new Ir.Expr.Binary("/",
                                                        new Ir.Expr.Variable("number", Type.INTEGER),
                                                        new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER),
                                                        Type.INTEGER
                                                ),
                                                new Ir.Expr.Literal(new BigInteger("5"), Type.INTEGER),
                                                Type.INTEGER
                                        ),
                                        new Ir.Expr.Variable("number", Type.INTEGER),
                                        Type.BOOLEAN
                                )
                        )),

                        new Ir.Stmt.If(
                            new Ir.Expr.Binary("AND", new Ir.Expr.Variable("mod3", Type.BOOLEAN), new Ir.Expr.Variable("mod5", Type.BOOLEAN), Type.BOOLEAN),
                            List.of(new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("FizzBuzz", Type.STRING)), Type.NIL))),
                            List.of(
                                    new Ir.Stmt.If(
                                            new Ir.Expr.Variable("mod3", Type.BOOLEAN),
                                            List.of(new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Fizz", Type.STRING)), Type.NIL))),
                                            List.of(
                                                    new Ir.Stmt.If(
                                                            new Ir.Expr.Variable("mod5", Type.BOOLEAN),
                                                            List.of(new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Literal("Buzz", Type.STRING)), Type.NIL))),
                                                            List.of(new Ir.Stmt.Expression(new Ir.Expr.Function("print", List.of(new Ir.Expr.Variable("number", Type.INTEGER)), Type.NIL)))
                                                    )
                                            )
                                    )
                            )
                        )
                ))
            )

        );
    }

    @ParameterizedTest
    @MethodSource
    void testRequireSubtype(String test, Type type, Type other, boolean expected) {
        try {
            Analyzer.requireSubtype(type, other);
            Assertions.assertTrue(expected, "Expected " + type + " to not be a subtype of " + other + ".");
        } catch (AnalyzeException e) {
            Assertions.assertFalse(expected, "Unexpected exception, expected " + type + " to be a subtype of " + other + ".");
        }
    }

    public static Stream<Arguments> testRequireSubtype() {
        return Stream.of(
            Arguments.of("Equal", Type.STRING, Type.STRING, true),
            Arguments.of("Subtype", Type.STRING, Type.ANY, true),
            Arguments.of("Supertype", Type.ANY, Type.STRING, false),
            Arguments.of("Nil Equal", Type.NIL, Type.NIL, true),
            Arguments.of("Nil Subtype", Type.NIL, Type.ANY, true),
            Arguments.of("Equatable Subtype", Type.STRING, Type.EQUATABLE, true),
            Arguments.of("Equatable Supertype", Type.ANY, Type.EQUATABLE, false),
            Arguments.of("Comparable Subtype", Type.STRING, Type.COMPARABLE, true),
            Arguments.of("Comparable Non-Subtype", Type.NIL, Type.COMPARABLE, false),

            Arguments.of("Comparable Equatable", Type.COMPARABLE, Type.EQUATABLE, true),
            Arguments.of("Equatable Comparable", Type.EQUATABLE, Type.COMPARABLE, false),
            Arguments.of("Iterable Comparable", Type.ITERABLE, Type.COMPARABLE, false),
            Arguments.of("Iterable Equatable", Type.ITERABLE, Type.EQUATABLE, true)

        );
    }

    interface ParserMethod<T extends Ast> {
        T invoke(Parser parser) throws ParseException;
    }

    /**
     * Test function for the Evaluator. The {@link Input} behaves the same as
     * in parser tests, but will now rely on the parser behavior too. This
     * function tests both the return value of evaluation and evaluation order
     * via the use of a custom log function that tracks invocations.
     */
    private static void test(Input input, @Nullable Ir expected, ParserMethod<? extends Ast> method) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Input.Ast i -> i.ast();
            case Input.Program i -> Assertions.assertDoesNotThrow(
                () -> method.invoke(new Parser(new Lexer(i.program).lex()))
            );
        };
        //Next, initialize the analyzer and scope.
        var scope = new Scope(Environment.scope());
        Analyzer analyzer = new Analyzer(scope);
        //Then, analyze the input and check the return value.
        try {
            var ir = analyzer.visit(ast);
            if (expected == null) {
                Assertions.fail("Expected an exception to be thrown, received " + ir + ".");
            }
            Assertions.assertEquals(expected, ir);
        } catch (AnalyzeException e) {
            if (expected != null) {
                Assertions.fail("Unexpected AnalyzeException thrown (" + e.getMessage() + "), expected " + expected + ".");
            }
        }
    }

}
