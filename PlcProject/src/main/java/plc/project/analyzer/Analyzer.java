package plc.project.analyzer;

import plc.project.evaluator.EvaluateException;
import plc.project.evaluator.RuntimeValue;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

public final class Analyzer implements Ast.Visitor<Ir, AnalyzeException> {

    private Scope scope;

    public Analyzer(Scope scope) {
        this.scope = scope;
    }

    @Override
    public Ir.Source visit(Ast.Source ast) throws AnalyzeException {
        var statements = new ArrayList<Ir.Stmt>();
        for (var statement : ast.statements()) {
            statements.add(visit(statement));
        }
        return new Ir.Source(statements);
    }

    private Ir.Stmt visit(Ast.Stmt ast) throws AnalyzeException {
        return (Ir.Stmt) visit((Ast) ast); //helper to cast visit(Ast.Stmt) to Ir.Stmt
    }

    @Override
    public Ir.Stmt.Let visit(Ast.Stmt.Let ast) throws AnalyzeException {
        // Name, Type, and Value
        //LET name (: Type)? (= exp)?;
            ///Evaluation Order Does have side effects and order should be accounted for
                /// Generally go left to right!
            ///Checked Exceptions in Java won't let us use  "ast.value().map(this::visit)"
        //If var is defined or not
        if(scope.get(ast.name(), true).isPresent()) {
            throw new AnalyzeException("Variable " + ast.name() + " is already defined in scope");
        }
        //TODO: Types (inheritive)
        Optional<Type> type = Optional.empty();
        if(ast.type().isPresent()){
            //This could be null
            //Safe version
            if(!Environment.TYPES.containsKey(ast.type().get())) {
                throw new AnalyzeException("Not Defined");
            }
            type = Optional.of(Environment.TYPES.get(ast.type().get()));
            //Alt Safer for threading
            //if(type != null) {
            //   throw new AnalyzeException("Not Defined");
            //}
        }
        Optional<Ir.Expr> value = ast.value().isPresent()
                ? Optional.of(visit(ast.value().get()))
                : Optional.empty();
        ///LOK AT THIS LOGIC
        var variableType = type.
                or(() -> value.map(expr -> expr.type())).
                orElse(Type.ANY);
        if(value.isPresent()) {
            requireSubtype(value.get().type(), variableType);
        }
        // Define the Variable (Needs to have info about scoping in variables also!
            // Even if it may not be aparent from the Analyzer Tests)
        scope.define(ast.name(), variableType);
        return new Ir.Stmt.Let(ast.name(), variableType, value);
    }

    @Override
    public Ir.Stmt.Def visit(Ast.Stmt.Def ast) throws AnalyzeException {
        /** Analyzes a DEF statement, with the following behavior:
                1. Define the function name (which must not already be defined)
                    in the current scope with a type of Type.Function.
                        1. Parameter names must be unique.
                        2. Parameter types and the function's returns type must all be in Environment.TYPES;
                            not provided explicitly the type is Any.
                2. In a new child scope:
                    1. Define variables for all parameters.
                    2. Define the variable $RETURNS (which cannot be used as a variable in our language)
                        to store the return type (see Stmt.Return).
                    3. Analyze all body statements sequentially.
         **/
        if(scope.get(ast.name(), true).isPresent()) {
            throw new AnalyzeException("Function Name: " + ast.name() + " is already defined in scope");
        }
        // 1. Ensure names in parameters are unique.
        // 2. Parameter types and the function's returns type must all be in Environment.TYPES;
            // not provided explicitly the type is Any.
        Set<String> uniqueNames = new HashSet<>();
        for(int i = 0; i < ast.parameters().size(); i++) {
            var type = Optional.of(Environment.TYPES.get(ast.parameterTypes().get(i)));
//            var pramType = type.
//                    or(() -> .map(expr -> expr.type())).
//                    orElse(Type.ANY);
            if(!uniqueNames.add(ast.parameters().get(i)))
                throw new AnalyzeException("Function Definition Does not have unique parameters: " + ast.name() + "().");
        }
            return null;
    }

    @Override
    public Ir.Stmt.If visit(Ast.Stmt.If ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.For visit(Ast.Stmt.For ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.Return visit(Ast.Stmt.Return ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Stmt.Expression visit(Ast.Stmt.Expression ast) throws AnalyzeException {
        var expression = visit(ast.expression());
        return new Ir.Stmt.Expression(expression);
    }

    @Override
    public Ir.Stmt.Assignment visit(Ast.Stmt.Assignment ast) throws AnalyzeException {
        if(ast.expression() instanceof Ast.Expr.Variable variable) {
            var ir = visit(variable);
            var value = visit(ast.value());
            requireSubtype(value.type(), ir.type());
           return new Ir.Stmt.Assignment.Variable(ir, value);
        }
        throw new AnalyzeException("Write Messsage"); //TODO
    }

    private Ir.Expr visit(Ast.Expr ast) throws AnalyzeException {
        return (Ir.Expr) visit((Ast) ast); //helper to cast visit(Ast.Expr) to Ir.Expr
    }

    @Override
    public Ir.Expr.Literal visit(Ast.Expr.Literal ast) throws AnalyzeException {
        var type = switch (ast.value()) {
            //The underscore in this case will pattern check for the type
                //The "_" is a don't care
            case null -> Type.NIL;
            case Boolean _ -> Type.BOOLEAN;
            case BigInteger _ -> Type.INTEGER;
            case BigDecimal _ -> Type.DECIMAL;
            case String _ -> Type.STRING;
            //If the AST value isn't one of the above types, the Parser is
            //returning an incorrect AST - this is an implementation issue,
            //hence throw AssertionError rather than AnalyzeException.
            default -> throw new AssertionError(ast.value().getClass());
        };
        return new Ir.Expr.Literal(ast.value(), type);
    }

    @Override
    public Ir.Expr.Group visit(Ast.Expr.Group ast) throws AnalyzeException {
        //Analyzes the contained expression with no additional restrictions.
        var exp = visit(ast.expression());
        return new Ir.Expr.Group(exp);
    }


    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException {
        //Analyzes a binary expression, with the following behavior:
        switch(ast.operator()) {
            /** "+"
             * If either operand is a String, the result is a String.
             * Otherwise, the left operand must be a subtypes of Integer/Decimal
             * and right must be the same type,
             * which is also the result type.
             * */
            case "+": {
                var left = visit(ast.left());
                var right = visit(ast.right());
                if(left.type().equals(Type.STRING) || right.type().equals(Type.STRING)) {
                    return new Ir.Expr.Binary(ast.operator(), left, right, Type.STRING);
                } else if (left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) {
                    if(left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) {
                        if(right.type().equals(left.type())) {
                            return new Ir.Expr.Binary(ast.operator(), left, right, left.type());
                        }else
                            throw new AnalyzeException("Not same type as left");
                    }else
                        throw new AnalyzeException("Not Integer or decimal");
                }
            }
            /** "-,*,/"
             * The left operand must be either an Integer/Decimal
             * and the right must be the same type,
             * which is also the result type.
             * */
            case "-", "*", "/": {
                var left = visit(ast.left());
                if(left.type().equals(Type.INTEGER) || left.type().equals(Type.DECIMAL)) {
                    var right = visit(ast.right());
                    if(right.type().equals(left.type())) {
                        return new Ir.Expr.Binary(ast.operator(), left, right, left.type());
                    }else
                        throw new AnalyzeException("Not same type as left");
                }else
                    throw new AnalyzeException("Not Integer or decimal");
            }
            /** <,<=, >=, >
             * The left operand must be a subtype of Comparable
             * and the right must be the same type.
             * The result is a Boolean.
             * */
            case "<", "<=", ">", ">=": {
                var left = visit(ast.left());
                if(isComparable(left.type())) {
                    var right = visit(ast.right());
                    requireSubtype(right.type(), left.type());  // or the other way around
                    return new Ir.Expr.Binary(ast.operator(), left, right, Type.BOOLEAN);
                }else
                    throw new AnalyzeException("Not Equatable");
            }
            /** ==, !=
             * The left operand must be a subtype of Comparable
             * and the right must be the same type.
             * The result is a Boolean.
             * */
            case "==", "!=": {
                var left = visit(ast.left());
                if(isEquatable(left.type())) {
                    var right = visit(ast.right());
                    requireSubtype(right.type(), left.type());  // or the other way around
                    return new Ir.Expr.Binary(ast.operator(), left, right, Type.BOOLEAN);
                }else
                    throw new AnalyzeException("Not Equatable");
            }
            /** AND/OR:
             * Both operands must be a subtype of Boolean.
             * The result is a Boolean.
             * */
            case "AND", "OR": {
                var left = visit(ast.left());
                if(left.type().equals(Type.BOOLEAN)) {
                    var right = visit(ast.right());
                    if(right.type().equals(left.type())) {
                        return new Ir.Expr.Binary(ast.operator(), left, right, left.type());
                    }else
                        throw new AnalyzeException("Not same type as left");
                }else
                    throw new AnalyzeException("Not Boolean");
            }

        }
        throw new AnalyzeException("TODO"); //TODO
    }

    @Override
    public Ir.Expr.Variable visit(Ast.Expr.Variable ast) throws AnalyzeException {
        // Simlar to the  Evaluator
        // Look for variables in all scope:
        var type = scope.get(ast.name(), false) /// "var" is an optional type in Java!!!!
                .orElseThrow(() -> new AnalyzeException("Variable " + ast.name() + "is not defined"));
        return new Ir.Expr.Variable(ast.name(), type);
    }

    @Override
    public Ir.Expr.Property visit(Ast.Expr.Property ast) throws AnalyzeException {
        /**Analyzes a variable expression, with the following behavior:
                1. Analyze the receiver, which must be an instanceof Type.Object.
                2. Ensure that name is defined in the object's scope and resolve it's type.
         */
        var receiver = visit(ast.receiver());
        if(receiver.type() instanceof Type.Object objectType) {
            var propertyType = objectType.scope().get(ast.name(), false)
                    .orElseThrow(() -> new AnalyzeException("Property '" + ast.name() + "' not found in object"));
            return new Ir.Expr.Property(receiver, ast.name(), propertyType);
        }else
            throw new AnalyzeException("Reciever " + ast.name() + " is not an instance of an Object");
    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException {
        /** 	Analyzes a function expression, with the following behavior:
                    1. Ensure the function name is defined and resolve it's type, which must be an instanceof Type.Function.
                    2. Analyze all arguments sequentially, ensuring that each argument is a subtype of the function's
                        corresponding parameter type.
                    3. The expression type is the function type's return type.
         * */
        var functionType = scope.get(ast.name(), false)
                .orElseThrow(() -> new AnalyzeException("Property '" + ast.name() + "' not found in object"));
        if(functionType instanceof Type.Function type){
            List<Ir.Expr> args = new ArrayList<>();
            for(int i = 0; i < ast.arguments().size(); i++) {
                args.add(visit(ast.arguments().get(i)));
                requireSubtype(args.get(i).type(), ((Type.Function) functionType).parameters().get(i));
            }
            return new Ir.Expr.Function(ast.name(), args, ((Type.Function) functionType).returns());
        }else
            throw new AnalyzeException("Function " + ast.name() + " is not an instance of an Object"); //TODO
    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException {
        /** Analyzes a function expression, with the following behavior:
                1. Analyze the receiver, which must be an instanceof Type.Object.
                2. Ensure the function name is defined in the object's scope and resolve it's type,
                    which must be an instanceof Type.Function.
                3. Analyze all arguments sequentially, ensuring that each argument
                    is a subtype of the function's corresponding parameter type.
                        Important: Unlike the Evaluator, the types for methods will not have the receiver
                        as a parameter (thus, arguments and parameters should have the same size).
                        See the changelog for details.
                4. The expression type is the function type's return type.
         **/
        var receiver = visit(ast.receiver());
        if(receiver.type() instanceof Type.Object objectType) {
            var methodType = objectType.scope().get(ast.name(), false)
                    .orElseThrow(() -> new AnalyzeException("Property '" + ast.name() + "' not found in object"));
            if(methodType instanceof Type.Function type){
                List<Ir.Expr> args = new ArrayList<>();
                for(int i = 0; i < ast.arguments().size(); i++) {
                    args.add(visit(ast.arguments().get(i)));
                    requireSubtype(args.get(i).type(), ((Type.Function) methodType).parameters().get(i));
                }
                return new Ir.Expr.Method(receiver, ast.name(), args, ((Type.Function) methodType).returns());
            }else
                throw new AnalyzeException("Function " + ast.name() + " is not an instance of an function type"); //TODO
        }else
            throw new AnalyzeException("Reciever " + ast.name() + " is not an instance of an Object");
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    public static void requireSubtype(Type type, Type other) throws AnalyzeException {
        // All types are subtypes of Any (similar to Java's Object).
        if(type.equals(other) || other.equals(Type.ANY)) {
            return;
        }
        // Nil, Comparable (and all subtypes), Iterable are subtypes of Equatable.
        if(other.equals(Type.COMPARABLE)) {
            if(isComparable(type)) {
                return;
            }
        }
        // Boolean, Integer, Decimal, String are subtypes of Comparable.
        if(other.equals(Type.EQUATABLE)) {
            if(isEquatable(type)){
                return;
            }
        }
        throw new AnalyzeException("Type " +  type + " is not a subtype of " + other);
    }

    private static boolean isComparable(Type type) {
        return type.equals(Type.BOOLEAN)
                || type.equals(Type.INTEGER)
                || type.equals(Type.DECIMAL)
                || type.equals(Type.STRING);
    }

    private static boolean isEquatable(Type type) {
        return type.equals(Type.NIL)
                || type.equals(Type.COMPARABLE)
                || type.equals(Type.ITERABLE)
                || isComparable(type);  // Because Comparable subtypes are Equatable
    }
}
