package plc.project.generator;

import plc.project.analyzer.Ir;
import plc.project.analyzer.Type;

import java.math.BigDecimal;
import java.math.BigInteger;

public class Generator implements Ir.Visitor<StringBuilder, RuntimeException> {

    private final StringBuilder builder = new StringBuilder();
    private int indent = 0;

    private void newline(int indent) {
        builder.append("\n");
        builder.append("    ".repeat(indent));
    }

    @Override
    public StringBuilder visit(Ir.Source ir) {
        builder.append(Environment.imports()).append("\n\n");
        builder.append("public final class Main {").append("\n\n");
        builder.append(Environment.definitions()).append("\n");
        //Java doesn't allow for nested functions, but we will pretend it does.
        //To support simple programs involving functions, we will "hoist" any
        //variable/function declaration at the start of the program to allow
        //these functions to be used as valid Java.
        indent = 1;
        boolean main = false;
        for (var statement : ir.statements()) {
            newline(indent);
            if (!main) {
                if (statement instanceof Ir.Stmt.Let || statement instanceof Ir.Stmt.Def) {
                    builder.append("static ");
                } else {
                    builder.append("public static void main(String[] args) {");
                    main = true;
                    indent = 2;
                    newline(indent);
                }
            }
            visit(statement);
        }
        if (main) {
            builder.append("\n").append("    }");
        }
        indent = 0;
        builder.append("\n\n").append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Let ir) {
        /**<Type> <name>;
         <Type> <name> = <value>;
         var <name> = <value>; (if Type is an instanceof Type.Object, to allow proper type inference in Java)
         * */
        if(ir.type() instanceof Type.Object){
            builder.append("var " + ir.name());
        }else
            builder.append(ir.type().jvmName() + " " + ir.name());

        if(ir.value().isPresent()) {
            builder.append(" = ");
            visit(ir.value().get());
        }

        builder.append(";");

        return builder; //TODO
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Def ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Stmt.If ir) {
        builder.append("if (");
        visit(ir.condition());
        builder.append(") {");
        ++indent;
        for(int i = 0; i < ir.thenBody().size(); i++) {
            newline(indent);
            visit(ir.thenBody().get(i));
        }
        newline(--indent);
        builder.append("}");
        if(!ir.elseBody().isEmpty()) {
            builder.append(" else {");
            ++indent;
            for (int i = 0; i < ir.thenBody().size(); i++) {
                newline(indent);
                visit(ir.elseBody().get(i));
            }
            newline(--indent);
            builder.append("}");
        }
        return builder;
    }

    //TODO: Test!
    @Override
    public StringBuilder visit(Ir.Stmt.For ir) {
        /**
         * for (<Type> <name> : <expression>) {
         *     <statements...> (separated by newlines)
         * }
         * */
        builder.append("for (" + ir.type().jvmName() + " " + ir.name() + " : ");
        visit(ir.expression());
        builder.append(") {");
        ++indent;
        for(var statement : ir.body()) {
            newline(indent);
            visit(statement);
        }
        newline(--indent);
        builder.append("}");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Return ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Expression ir) {
        visit(ir.expression());
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Variable ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Property ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Expr.Literal ir) {
        var literal = switch (ir.value()) {
            case null -> "null";
            case Boolean b -> b.toString();
            case BigInteger i -> "new BigInteger(\"" + i + "\")";
            case BigDecimal d -> "new BigDecimal(\"" + d + "\")";
            case String s -> "\"" + s + "\""; //TODO: Escape characters?
            //If the IR value isn't one of the above types, the Parser/Analyzer
            //is returning an incorrect IR - this is an implementation issue,
            //hence throw AssertionError rather than a "standard" exception.
            default -> throw new AssertionError(ir.value().getClass());
        };
        builder.append(literal);
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Group ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Expr.Binary ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Expr.Variable ir) {
        builder.append(ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Property ir) {
        builder.append("." + ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Function ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Expr.Method ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

    @Override
    public StringBuilder visit(Ir.Expr.ObjectExpr ir) {
        throw new UnsupportedOperationException("TODO"); //TODO
    }

}
