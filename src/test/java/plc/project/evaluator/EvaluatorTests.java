package plc.project.evaluator;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Standard JUnit5 parameterized tests. See the RegexTests file from Homework 1
 * or the LexerTests file from the earlier project part for more information.
 */
final class EvaluatorTests {

    public sealed interface Input {
        record Ast(plc.project.parser.Ast ast) implements Input {}
        record Program(String program) implements Input {}
    }

    @ParameterizedTest
    @MethodSource
    void testSource(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testSource() {
        return Stream.of(
            Arguments.of("Single",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("value"))))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Multiple",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("1"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("2"))))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("3")))))
                ))),
                new RuntimeValue.Primitive(new BigInteger("3")),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Source.
            Arguments.of("Unhandled Return",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                null, //EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testLetStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testLetStmt() {
        return Stream.of(
            Arguments.of("Declaration",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive(null))
            ),
            Arguments.of("Initialization",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.of(new Ast.Expr.Literal("value"))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("name"))))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Redefined",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Let("name", Optional.empty()),
                    new Ast.Stmt.Let("name", Optional.empty())
                ))),
                null,
                List.of()
            ),
            Arguments.of("Shadowed",
                new Input.Ast(new Ast.Source(List.of(
                    //"variable" is defined to "variable" in Environment.scope()
                    new Ast.Stmt.Let("variable", Optional.empty())
                ))),
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            //Value Scope Undefined (1): LET undefined = undefined;
            Arguments.of("Value Scope Undefined",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Let("undefined", Optional.of(new Ast.Expr.Variable("undefined"))),
                            new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("undefined"))))
                    ))),
                    null,  // This should cause an error or be handled properly in runtime
                    List.of()
            ),
            //Value Scope Shadowing (1): LET variable = variable;
            Arguments.of("Value Scope Shadowing",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Let("variable", Optional.of(new Ast.Expr.Variable("variable"))),
                            new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("variable"))))
                    ))),
                    new RuntimeValue.Primitive("variable"), // The shadowed variable retains the scope value
                    List.of(new RuntimeValue.Primitive("variable"))
            ),
            Arguments.of("Variable Already Defined in Current Scope",
                    new Input.Program("""
                    LET var = 1;
                    LET var = 2;
            """),
                        null,
                        List.of()
                )
        );
    }
    @ParameterizedTest
    @MethodSource
    void testDefStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testDefStmt() {
        return Stream.of(
            Arguments.of("Invocation",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("invoked"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("invoked"))
            ),
            Arguments.of("Parameter",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of("parameter"), List.of(
                        new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("parameter"))))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("argument"))
            ),
            //Duplicated in testReturnStmt, but is part of the spec for Def.
            Arguments.of("Return Value",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of()
            ),
            Arguments.of("Scope Exit Restored",
                    new Input.Program("""
                    LET x = 1;
                    DEF modifyX() DO
                        x = 3;
                    END
                    modifyX();
                    log(x);
                    """),
                    new RuntimeValue.Primitive(new BigInteger("3")),
                    List.of(new RuntimeValue.Primitive(new BigInteger("3"))) // x should remain 10 after function call
            ),
                //Scope Exception Restored
            Arguments.of("Scope Exit Exception Restored",
                    new Input.Program("""
                        LET x = 1;
                        DEF error() DO
                            undefined;
                        END
                        error();
                        log(x);
                        """),
                    null,
                    //Should Not log X
                    List.of()
            ),
            //Arguments are evaluated sequentially
            Arguments.of("Arguments Evaluated Sequentially",
                    new Input.Program("""
                        LET x = 1;
                        DEF f(a, b) DO
                            log(a);
                            log(b);
                        END
                        f(1,2);
                        """),
                    new RuntimeValue.Primitive(null),
                    List.of(new RuntimeValue.Primitive(new BigInteger("1")), new RuntimeValue.Primitive(new BigInteger("2"))) // x should remain 10 after function call
            ),
            Arguments.of("Static Scoping",
                    new Input.Program("""
                    LET x = 1;
                    DEF outer() DO
                        LET x = 42;
                        DEF inner() DO
                            log(x);
                        END
                        inner();
                    END
                    outer();
                    """),
                    new RuntimeValue.Primitive(null),
                    List.of(new RuntimeValue.Primitive(new BigInteger("42"))) // x should remain 10 after function call
            ),
            Arguments.of("PER SPEC: Static Scoping",
                    new Input.Program("""
                    LET scope = "outer";
                    DEF function() DO
                        log(scope);
                    END
                    DEF main() DO
                        LET scope = "inner";
                        function();
                    END
                    main();
                    """),
                    new RuntimeValue.Primitive(null),
                    List.of(new RuntimeValue.Primitive("outer")) // x should remain 10 after function call
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testIfStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testIfStmt() {
        return Stream.of(
            Arguments.of("Then",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(true),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                ))),
                new RuntimeValue.Primitive("then"),
                List.of(new RuntimeValue.Primitive("then"))
            ),
            Arguments.of("Else",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.If(
                        new Ast.Expr.Literal(false),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("then"))))),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("else")))))
                    )
                ))),
                new RuntimeValue.Primitive("else"),
                List.of(new RuntimeValue.Primitive("else"))
            ),

            //Then Scope Access (1): LET scope = "outer"; IF TRUE DO log(scope); END
            Arguments.of("Then Scope Access",
                    new Input.Ast(new Ast.Source(List.of(
                            new Ast.Stmt.Let("scope", Optional.of(new Ast.Expr.Literal("outer"))),
                            new Ast.Stmt.If(
                                    new Ast.Expr.Literal(true),
                                    List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("scope"))))),
                                    List.of()
                            )
                    ))),
                    new RuntimeValue.Primitive("outer"),
                    List.of(new RuntimeValue.Primitive("outer"))
            ),
            //Then Scope Assignment (1): LET scope = "outer"; IF TRUE DO scope = "inner"; END log(scope);
            Arguments.of("Then Scope Assignment",
                    new Input.Program("""
                        LET scope = "outer"; IF TRUE DO scope = "inner"; END log(scope);
                    """),
                    new RuntimeValue.Primitive("inner"),
                    List.of(new RuntimeValue.Primitive("inner"))
            ),
            //Then Scope Exit Restored (1): LET scope = "outer"; IF TRUE DO LET scope = "inner"; END log(scope);
            Arguments.of("Then Scope Exit Restored",
                    new Input.Program("""
                        LET scope = "outer"; 
                        IF TRUE DO 
                            LET scope = "inner"; 
                            log(scope); 
                        END 
                        log(scope);
                    """),
                    new RuntimeValue.Primitive("outer"),
                    List.of(new RuntimeValue.Primitive("inner"),new RuntimeValue.Primitive("outer"))
            ),
                Arguments.of("Then Scope Exit - else - Restored",
                        new Input.Program("""
                        LET scope = "outer"; 
                        IF FALSE DO
                        ELSE
                            LET scope = "inner"; 
                            log(scope); 
                        END 
                        log(scope);
                        """),
                        new RuntimeValue.Primitive("outer"),
                        List.of(new RuntimeValue.Primitive("inner"),new RuntimeValue.Primitive("outer"))
                ),
            //Then Scope Exception Restored (1): LET scope = "outer"; IF TRUE DO LET scope = "inner"; undefined; END (should throw), then log(scope);
            Arguments.of("Then Scope Exception Restored",
                    new Input.Program("""
                        LET scope = "outer"; 
                        IF TRUE DO 
                            LET scope = "inner"; 
                            undefined; 
                        END 
                        log(scope);
                    """),
                    //Should this not print log(scope);?
                    null,
                    List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testForStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testForStmt() {
        return Stream.of(
            Arguments.of("For",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.For(
                        "element",
                        new Ast.Expr.Function("list", List.of(
                            new Ast.Expr.Literal(new BigInteger("1")),
                            new Ast.Expr.Literal(new BigInteger("2")),
                            new Ast.Expr.Literal(new BigInteger("3"))
                        )),
                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("element")))))
                    )
                ))),
                new RuntimeValue.Primitive(null),
                List.of(
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    new RuntimeValue.Primitive(new BigInteger("2")),
                    new RuntimeValue.Primitive(new BigInteger("3"))
                )
            ),
            //FOR i IN range(1, 5) DO log(i); END
            Arguments.of("The Range Function",
                        new Input.Ast(new Ast.Source(List.of(
                                new Ast.Stmt.For(
                                        "i",
                                        new Ast.Expr.Function("range", List.of(
                                                new Ast.Expr.Literal(new BigInteger("1")),
                                                new Ast.Expr.Literal(new BigInteger("5"))
                                        )),
                                        List.of(new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("i")))))
                                )
                    ))),
                    new RuntimeValue.Primitive(null),
                    List.of(
                            new RuntimeValue.Primitive(new BigInteger("1")),
                            new RuntimeValue.Primitive(new BigInteger("2")),
                            new RuntimeValue.Primitive(new BigInteger("3")),
                            new RuntimeValue.Primitive(new BigInteger("4")),
                            new RuntimeValue.Primitive(new BigInteger("5"))
                    )
            ),
            Arguments.of("Not an iterable expression",
                    new Input.Program("""
                    FOR e IN variable DO log(e); UNDEFINED; END log(variable);
                    """),
                    null,
                    //Should Not log X
                    List.of()
            ),
            Arguments.of("Scope Exit Exception Restored",
                    new Input.Program("""
                    FOR e IN list(1, 2, 3) DO log(e); UNDEFINED; END log(variable);
                    """),
                    null,
                    //Should Not log X
                    List.of(new RuntimeValue.Primitive(new BigInteger("1")))
            ),
            Arguments.of("Range more than one argument",
                    new Input.Program("""
                    range(1,2,3);
                    """),
                    null,
                    //Should Not log X
                    List.of()
            ),
            Arguments.of("Range has incorrect Types",
                    new Input.Program("""
                    range(1,TRUE);
                    """),
                    null,
                    //Should Not log X
                    List.of()
            ),
            Arguments.of("Scope Exit Restored",
                    new Input.Program("""
                    LET scope = "outer";
                    FOR e IN list(1, 2) DO
                        LET scope = "inner";
                        log(scope);
                    END 
                    log(scope);
                    """),
                    new RuntimeValue.Primitive("outer"),
                    //Should Not log X
                    List.of(new RuntimeValue.Primitive("inner"),
                            new RuntimeValue.Primitive("inner"),
                            new RuntimeValue.Primitive("outer"))
            )
        );


    }

    @ParameterizedTest
    @MethodSource
    void testReturnStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testReturnStmt() {
        return Stream.of(
            //Part of the spec for Def, but duplicated here for clarity.
            Arguments.of("Inside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Def("name", List.of(), List.of(
                        new Ast.Stmt.Return(Optional.of(new Ast.Expr.Literal("value")))
                    )),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("name", List.of()))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of()
            ),
            Arguments.of("Return NIL",
                    new Input.Program("""
                    DEF F() DO 
                        RETURN NIL;
                    END
                    F();
                    """
                    ),
                    new RuntimeValue.Primitive(null),
                    List.of()
            ),
            //Part of the spec for Source, but duplicated here for clarity.
            Arguments.of("Outside Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Return(Optional.empty())
                ))),
                null, //EvaluateException
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testExpressionStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    private static Stream<Arguments> testExpressionStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Variable("variable"))
                ))),
                new RuntimeValue.Primitive("variable"),
                List.of()
            ),
            Arguments.of("Function",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Expression(new Ast.Expr.Function("function", List.of(new Ast.Expr.Literal("argument"))))
                ))),
                new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument"))),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testAssignmentStmt(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    //TODO: Compare with Specification
    private static Stream<Arguments> testAssignmentStmt() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Variable("variable"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(new Ast.Expr.Variable("variable"))))
                ))),
                new RuntimeValue.Primitive("value"),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Property",
                new Input.Ast(new Ast.Source(List.of(
                    new Ast.Stmt.Assignment(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property"),
                        new Ast.Expr.Literal("value")
                    ),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("log", List.of(
                        new Ast.Expr.Property(new Ast.Expr.Variable("object"), "property")
                    ))),
                    new Ast.Stmt.Expression(new Ast.Expr.Function("nil", List.of()))
                ))),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("value"))
            ),
            Arguments.of("Can only assing primitive to poperty or variable",
                    new Input.Program("""
                    1 = "value";
            """),
                    null,
                    List.of()
            ),
            Arguments.of("Undefined",
                    new Input.Program("""
                    undefined = "value";
            """),
                    null,
                    List.of()
            ),
            Arguments.of("Variable Undefined Log",
                    new Input.Program("""
                    undefined = log("unevaluated");
            """),
                    null,
                    List.of()
            ),
            Arguments.of("Property Undefined Log",
                    new Input.Program("""
                    object.undefined = log("unevaluated");
            """),
                    null,
                    List.of()
            )

        );
    }

    @ParameterizedTest
    @MethodSource
    void testLiteralExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testLiteralExpr() {
        return Stream.of(
            Arguments.of("Boolean",
                new Input.Ast(
                    new Ast.Expr.Literal(true)
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Integer",
                new Input.Ast(
                    new Ast.Expr.Literal(new BigInteger("1"))
                ),
                new RuntimeValue.Primitive(new BigInteger("1")),
                List.of()
            ),
            Arguments.of("String",
                new Input.Ast(
                    new Ast.Expr.Literal("string")
                ),
                new RuntimeValue.Primitive("string"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testGroupExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testGroupExpr() {
        return Stream.of(
            Arguments.of("Group",
                new Input.Ast(
                    new Ast.Expr.Group(new Ast.Expr.Literal("expr"))
                ),
                new RuntimeValue.Primitive("expr"),
                List.of()
            ),
            Arguments.of("Nested Group",
                    new Input.Program("""
                (("exp"));
            """),
                    new RuntimeValue.Primitive("exp"),
                    List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testBinaryExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testBinaryExpr() {
        return Stream.of(
            Arguments.of("Op+ Integer Addition",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new RuntimeValue.Primitive(new BigInteger("3")),
                List.of()
            ),
            Arguments.of("Op+ Decimal Addition",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal(new BigDecimal("1.0")),
                        new Ast.Expr.Literal(new BigDecimal("2.0"))
                    )
                ),
                new RuntimeValue.Primitive(new BigDecimal("3.0")),
                List.of()
            ),
            Arguments.of("Op+ String Concatenation",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "+",
                        new Ast.Expr.Literal("left"),
                        new Ast.Expr.Literal("right")
                    )
                ),
                new RuntimeValue.Primitive("leftright"),
                List.of()
            ),
            Arguments.of("Op- Evaluation Order Left Validation Error",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "-",
                        new Ast.Expr.Literal("invalid"),
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("evaluated")))
                    )
                ),
                null, //EvaluateException
                List.of(new RuntimeValue.Primitive("evaluated"))
            ),
            Arguments.of("Op* Evaluation Order Left Execution Error",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "*",
                        new Ast.Expr.Variable("undefined"),
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(new BigInteger("1"))))
                    )
                ),
                null, //EvaluateException
                List.of()
            ),
            Arguments.of("Op/ Decimal Rounding Down",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "/",
                        new Ast.Expr.Literal(new BigDecimal("5")),
                        new Ast.Expr.Literal(new BigDecimal("2"))
                    )
                ),
                new RuntimeValue.Primitive(new BigDecimal("2")),
                List.of()
            ),
            Arguments.of("Op< Integer True",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "<",
                        new Ast.Expr.Literal(new BigInteger("1")),
                        new Ast.Expr.Literal(new BigInteger("2"))
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of()
            ),
            Arguments.of("Op== Decimal False",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "==",
                        new Ast.Expr.Literal(new BigDecimal("1.0")),
                        new Ast.Expr.Literal(new BigDecimal("2.0"))
                    )
                ),
                new RuntimeValue.Primitive(false),
                List.of()
            ),
            Arguments.of("OpAND False",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "AND",
                        new Ast.Expr.Literal(true),
                        new Ast.Expr.Literal(false)
                    )
                ),
                new RuntimeValue.Primitive(false),
                List.of()
            ),
            Arguments.of("OpOR True Short-Circuit",
                new Input.Ast(
                    new Ast.Expr.Binary(
                        "OR",
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(true))),
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal(false)))
                    )
                ),
                new RuntimeValue.Primitive(true),
                List.of(new RuntimeValue.Primitive(true))
            ),
            Arguments.of("Op+ String Concatenation String Right",
                    new Input.Program("""
                    1 + "string";
            """),
                    new RuntimeValue.Primitive("1string"),
                    List.of()
            ),
            Arguments.of("Op+ String Concatenation NIL",
                    new Input.Program("""
                    "" + NIL;
            """),
                    new RuntimeValue.Primitive("NIL"),
                    List.of()
            ),
            Arguments.of("Op/ Integer Divide By Zero Result",
                    new Input.Program("""
                    1 / 0;
                    """),
                    null,
                    List.of()
            ),
            Arguments.of("NIL == NIL",
                    new Input.Program("""
                    NIL == NIL;
                    """),
                    new RuntimeValue.Primitive(true),
                    List.of()
            ),
            Arguments.of("false test",
                    new Input.Program("""
                    2 == 3;
                    """),
                    new RuntimeValue.Primitive(false),
                    List.of()
            ),
            Arguments.of("Op/ Integer Divide By Zero Log",
                    new Input.Program("""
                    log(1) / log(0);
                    """),
                    null,
                    List.of( new RuntimeValue.Primitive(new BigInteger("1")),
                            new RuntimeValue.Primitive(new BigInteger("0")))
            ),
            Arguments.of("Op/ Decimal Rounding Up",
                    new Input.Program("""
                    2.3 / 2.0;
                    """),
                    new RuntimeValue.Primitive(new BigDecimal("1.2")),
                    List.of()
            ),
            Arguments.of("Op/ Mismatched Types",
                    new Input.Program("""
                    1 / 2.0;
                    """),
                        null,
                        List.of()
            ),
            Arguments.of("Group Expression Operation: PEMDAS",
                    new Input.Program("""
                    log(log((log(1) + log(4))) / log((log(2) + log(3))));
                    """),
                    new RuntimeValue.Primitive(new BigInteger("1")),
                    List.of(new RuntimeValue.Primitive(new BigInteger("1")),
                            new RuntimeValue.Primitive(new BigInteger("4")),
                            new RuntimeValue.Primitive(new BigInteger("5")),
                            new RuntimeValue.Primitive(new BigInteger("2")),
                            new RuntimeValue.Primitive(new BigInteger("3")),
                            new RuntimeValue.Primitive(new BigInteger("5")),
                            new RuntimeValue.Primitive(new BigInteger("1")))
            ),
            Arguments.of("concat with bool",
                    new Input.Program("""
                    "" + TRUE;
                    """),
                    new RuntimeValue.Primitive("TRUE"),
                    List.of()
            ),
            Arguments.of("concat with bigdecimal",
                    new Input.Program("""
                    "" + 1.0e4;
                    """),
                    new RuntimeValue.Primitive("1.0E+4"),
                    List.of()
            )
            //Incorrect types tests
        );
    }

    @ParameterizedTest
    @MethodSource
    void testVariableExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testVariableExpr() {
        return Stream.of(
            Arguments.of("Variable",
                new Input.Ast(
                    new Ast.Expr.Variable("variable")
                ),
                new RuntimeValue.Primitive("variable"),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testPropertyExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
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
                new RuntimeValue.Primitive("property"),
                List.of()
            ),
            Arguments.of("Not the right Property TYPES",
                    new Input.Ast(
                            new Ast.Expr.Property(
                                    new Ast.Expr.Literal(new BigDecimal("1.0")),
                                    "property"
                            )
                    ),
                    null,
                    List.of()
            ),
            Arguments.of("Undefined Property",
                    new Input.Ast(
                            new Ast.Expr.Property(
                                    new Ast.Expr.Variable("object"),
                                    "undefined"
                            )
                    ),
                    null,
                    List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testFunctionExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testFunctionExpr() {
        return Stream.of(
            Arguments.of("Function",
                new Input.Ast(
                    new Ast.Expr.Function("function", List.of())
                ),
                new RuntimeValue.Primitive(List.of()),
                List.of()
            ),
            Arguments.of("Argument",
                new Input.Ast(
                    new Ast.Expr.Function("function", List.of(
                        new Ast.Expr.Literal("argument")
                    ))
                ),
                new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument"))),
                List.of()
            ),
            Arguments.of("Undefined",
                new Input.Ast(
                    new Ast.Expr.Function("undefined", List.of(
                        new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("argument")))
                    ))
                ),
                null, //EvaluateException
                List.of()
            ),
            //undefined(log("unevaluated")
            Arguments.of("Undefined",
                    new Input.Ast(
                            new Ast.Expr.Function("undefined", List.of(
                                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("unevaluated")))
                            ))
                    ),
                    null, //EvaluateException
                    List.of()
            ),
            //list(log("evaluated"), undefined, log("unevaluated"))
            Arguments.of("Argument Evaluation Order",
                    new Input.Ast(
                            new Ast.Expr.Function("list", List.of(
                                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("evaluated"))),
                                    new Ast.Expr.Variable("Undefined"),
                                    new Ast.Expr.Function("log", List.of(new Ast.Expr.Literal("unevaluated")))
                            ))
                    ),
                    null, //EvaluateException
                    List.of(
                            new RuntimeValue.Primitive("evaluated")
                    )
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testMethodExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testMethodExpr() {
        return Stream.of(
            Arguments.of("Method",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.Variable("object"),
                        "method",
                        List.of(new Ast.Expr.Literal("argument"))
                    )
                ),
                new RuntimeValue.Primitive(List.of(new RuntimeValue.Primitive("argument"))),
                List.of()
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    void testObjectExpr(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseExpr);
    }

    private static Stream<Arguments> testObjectExpr() {
        return Stream.of(
            Arguments.of("Empty",
                new Input.Ast(
                    new Ast.Expr.ObjectExpr(
                        Optional.empty(),
                        List.of(),
                        List.of()
                    )
                ),
                new RuntimeValue.ObjectValue(Optional.empty(), new Scope(null)),
                List.of()
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
                new RuntimeValue.Primitive("value"),
                List.of()
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
                new RuntimeValue.Primitive(null),
                List.of()
            ),
            Arguments.of("Method Parameter",
                new Input.Ast(
                    new Ast.Expr.Method(
                        new Ast.Expr.ObjectExpr(
                            Optional.empty(),
                            List.of(),
                            List.of(new Ast.Stmt.Def(
                                "method",
                                List.of("parameter"),
                                List.of(new Ast.Stmt.Return(Optional.of(new Ast.Expr.Variable("parameter"))))
                            ))
                        ),
                        "method",
                        List.of(new Ast.Expr.Literal("argument"))
                    )
                ),
                new RuntimeValue.Primitive("argument"),
                List.of()
            )

        );
    }

    @ParameterizedTest
    @MethodSource
    void testProgram(String test, Input input, RuntimeValue expected, List<RuntimeValue> log) {
        test(input, expected, log, Parser::parseSource);
    }

    public static Stream<Arguments> testProgram() {
        return Stream.of(
            Arguments.of("Hello World",
                //Input.Program makes tests *significantly* easier, but relies
                //on your Lexer and Parser being implemented correctly!
                new Input.Program("""
                    DEF main() DO
                        log("Hello, World!");
                    END
                    main();
                    """),
                new RuntimeValue.Primitive(null),
                List.of(new RuntimeValue.Primitive("Hello, World!"))
            ),
            Arguments.of("Fizz Buzz",
                    //Input.Program makes tests *significantly* easier, but relies
                    //on your Lexer and Parser being implemented correctly!
                    new Input.Program("""
                    LET number = 5;
                    LET mod3 = number / 3 * 3 == number;
                    LET mod5 = number / 5 * 5 == number;
                    IF mod3 AND mod5 DO
                        log("FizzBuzz");
                    ELSE
                        IF mod3 DO
                            log("Fizz");
                        ELSE
                            IF mod5 DO
                                log("Buzz");
                            ELSE
                                log("" + number);
                            END
                        END
                    END
                    """),
                    new RuntimeValue.Primitive("Buzz"),
                    List.of(new RuntimeValue.Primitive("Buzz"))
            ),
                //object_expr ::= 'OBJECT' identifier? 'DO' let_stmt* def_stmt* 'END'
            Arguments.of("Then Scope Exception Restored",
                    new Input.Program("""
                    LET obj = OBJECT DO
                        LET scope = "outer";
                        DEF error() DO
                            LET scope = "inner";
                            undefined;
                        END
                    END;
                    obj.error();
                    log(obj.scope);
                    """),
                    null,
                    List.of()
            ),
            Arguments.of("Object \"this\" check: Dynamic Dispatch",
                    new Input.Program("""
                    LET obj = OBJECT DO
                        LET scope = "outer";
                        DEF f() DO
                            RETURN this.scope;
                        END
                    END;
                    obj.f();
                    """),
                    new RuntimeValue.Primitive("outer"),
                    List.of()
            ),
            Arguments.of("Can't Return in a new scope that is not an if",
                new Input.Program("""
                IF TRUE DO
                    RETURN varibale;
                END
                """),
                null,
                List.of()
            ),
            Arguments.of("Embedded Property and Method",
                    new Input.Program(
                            """
                            LET obj1 = OBJECT DO
                                LET obj2 = OBJECT DO
                                    LET property = "property";
                                    DEF f() DO
                                        RETURN NIL;
                                    END
                                END;
                            END;
                            log(obj1.obj2.f());
                            log(obj1.obj2.property);
                            """
                    ),
                    new RuntimeValue.Primitive("property"),
                    List.of(new RuntimeValue.Primitive(null),
                            new RuntimeValue.Primitive("property"))
            ),
            Arguments.of("PER SPEC: Static Scoping Object",
                    new Input.Program("""
                    LET scope = "outer";
                    LET obj = OBJECT DO
                        DEF f() DO
                            log(scope);
                        END
                    END;
                    DEF main() DO 
                        LET scope = "inner";
                        obj.f();
                    END
                    main();
                    """),
                    new RuntimeValue.Primitive(null),
                    List.of(new RuntimeValue.Primitive("outer")) // x should remain 10 after function call
            ),
            Arguments.of("Print other Runtime values",
                    new Input.Program("""
                    log("This is an Object: "  + object);
                    log("This a function: " + function);
                    """),
                    new RuntimeValue.Primitive("This a function: DEF function(?) DO ? END"),
                    List.of(new RuntimeValue.Primitive("This is an Object: Object(Object) { property = `property`, method = `DEF method(?) DO ? END` }"),
                            new RuntimeValue.Primitive("This a function: DEF function(?) DO ? END")) // x should remain 10 after function call
            )
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
    private static void test(Input input, @Nullable RuntimeValue expected, List<RuntimeValue> log, ParserMethod<? extends Ast> method) {
        //First, get/parse the input AST.
        var ast = switch (input) {
            case Input.Ast i -> i.ast();
            case Input.Program i -> Assertions.assertDoesNotThrow(
                () -> method.invoke(new Parser(new Lexer(i.program).lex()))
            );
        };
        //Next, initialize the evaluator and scope.
        var scope = new Scope(Environment.scope());
        //This one is a bit weird, but it allows statement tests to force NIL as
        //the return value for Ast.Source for reduced overlap in testing.
        scope.define("nil", new RuntimeValue.Function("nil", arguments -> {
            if (!arguments.isEmpty()) {
                throw new EvaluateException("Expected nil to be called with 0 arguments.");
            }
            return new RuntimeValue.Primitive(null);
        }));
        //Log allows tracking when expressions are evaluated, allowing tests to
        //also inspect the evaluation order and control flow.
        var logged = new ArrayList<RuntimeValue>();
        scope.define("log", new RuntimeValue.Function("log", arguments -> {
            if (arguments.size() != 1) {
                throw new EvaluateException("Expected log to be called with 1 argument.");
            }
            logged.add(arguments.getFirst());
            return arguments.getFirst();
        }));
        Evaluator evaluator = new Evaluator(scope);
        //Then, evaluate the input and check the return value.
        try {
            var value = evaluator.visit(ast);
            Assertions.assertNotNull(expected, "Expected an exception to be thrown, received " + value + ".");
            Assertions.assertEquals(expected, value);
        } catch (EvaluateException e) {
            Assertions.assertNull(expected, "Unexpected EvaluateException thrown (" + e.getMessage() +"), expected " + expected + ".");
        }
        //Finally, check the log results for evaluation order.
        Assertions.assertEquals(log, logged);
    }

}
