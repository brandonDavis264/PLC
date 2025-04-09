package plc.project.analyzer;

import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Optional;

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
        throw new UnsupportedOperationException("TODO"); //TODO
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
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Expr.Binary visit(Ast.Expr.Binary ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
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
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Expr.Function visit(Ast.Expr.Function ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Expr.Method visit(Ast.Expr.Method ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public Ir.Expr.ObjectExpr visit(Ast.Expr.ObjectExpr ast) throws AnalyzeException {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    public static void requireSubtype(Type type, Type other) throws AnalyzeException {
        if(type.equals(other) || other.equals(Type.ANY)) {
            return;
        }
        //TODO: Equatable and Comparable types
            //Cuople of if-Statements
        throw new AnalyzeException("Something about types"); //TODO
    }

}
