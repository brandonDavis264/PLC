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
        /**
         * <ReturnType> <name>(<First> <first>, <Second> <second>, <Third> <third>, ...) {
         *     <statements...>
         * }
         **/
        builder.append(ir.returns().jvmName() + " " + ir.name() + "(");
        for(int i = 0; i < ir.parameters().size(); i++){
            builder.append(ir.parameters().get(i).type().jvmName()
                    + " " + ir.parameters().get(i).name());
            if(i < ir.parameters().size() - 1)
                builder.append(", ");
        }
        builder.append(")");

        //body
        builder.append(" {");
        ++indent;
        for(var stamtment : ir.body()) {
            newline(indent);
            visit(stamtment);
        }
        newline(--indent);
        builder.append("}");

        return builder;
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
        //return null; (if value is empty)
        if(ir.value().isPresent()) {
            builder.append("return ");
            visit(ir.value().get());
            builder.append(";");
        }else{
            builder.append("return null;");
        }
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Expression ir) {
        visit(ir.expression());
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Variable ir) {
        //<variable> = <value>;
        visit(ir.variable());
        builder.append(" = ");
        visit(ir.value());
        builder.append(";");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Stmt.Assignment.Property ir) {
        //<property> = <value>;
        visit(ir.property());
        builder.append(" = ");
        visit(ir.value());
        builder.append(";");
        return builder;
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
        //(<expr>)
        builder.append("(");
        visit(ir.expression());
        builder.append(")");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Binary ir) {
        /***/
        switch(ir.operator()){
            case "+": {
                if(ir.type().equals(Type.STRING)){
                    visit(ir.left());
                    builder.append(" + ");
                    visit(ir.right());
                }else{
                    builder.append("(");
                    visit(ir.left());
                    builder.append(")");
                    builder.append(".add(");
                    visit(ir.right());
                    builder.append(")");
                }
                break;
            }
            case "-": {
                builder.append("(");
                visit(ir.left());
                builder.append(")");
                builder.append(".subtract(");
                visit(ir.right());
                builder.append(")");
                break;
            }
            case "*": {
                builder.append("(");
                visit(ir.left());
                builder.append(")");
                builder.append(".multiply(");
                visit(ir.right());
                builder.append(")");
                break;
            }
            case "/": {
                if(ir.type().equals(Type.INTEGER)){
                    builder.append("(");
                    visit(ir.left());
                    builder.append(")");
                    builder.append(".divide(");
                    visit(ir.right());
                    builder.append(")");
                }else{
                    builder.append("(");
                    visit(ir.left());
                    builder.append(")");
                    builder.append(".divide(");
                    visit(ir.right());
                    builder.append(", RoundingMode.HALF_EVEN)");
                }
                break;
            }
            case "<", "<=", ">", ">=": {
                //(<left>).compareTo(<right>) <op> 0
                builder.append("(");
                visit(ir.left());
                builder.append(")");
                builder.append(".compareTo(");
                visit(ir.right());
                builder.append(") " + ir.operator() + " 0");
                break;
            }
            case "==": {
                //Objects.equals(<left>, <right>)
                builder.append("Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");
                break;
            }
            case "!=": {
                //!Objects.equals(<left>, <right>)
                builder.append("!Objects.equals(");
                visit(ir.left());
                builder.append(", ");
                visit(ir.right());
                builder.append(")");
                break;
            }
            case "AND": {
                // If left is binary OR (<left>) && <right>
                if(ir.left() instanceof Ir.Expr.Binary leftBin
                        && leftBin.operator().equals("OR")){
                    builder.append("(");
                    visit(ir.left());
                    builder.append(") && ");
                    visit(ir.right());

                }else {
                    // <left> && <right>
                    visit(ir.left());
                    builder.append(" && ");
                    visit(ir.right());
                }
                break;
            }
            case "OR": {
                visit(ir.left());
                builder.append(" || ");
                visit(ir.right());
                break;
            }
        }
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Variable ir) {
        builder.append(ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Property ir) {
        //<receiver>.<name>
        visit(ir.receiver());
        builder.append("." + ir.name());
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Function ir) {
        /**
         * <name>()
         * <name>(<argument>)
         * <name>(<first>, <second>, <third>, ...)
         * */
        builder.append(ir.name());
        builder.append("(");
        for(int i = 0; i < ir.arguments().size(); i++){
            visit(ir.arguments().get(i));
            if(i < ir.arguments().size() - 1)
                builder.append(", ");
        }
        builder.append(")");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.Method ir) {
        /**
         * <receiver>.<name>()
         * <receiver>.<name>(<argument>)
         * <receiver>.<name>(<first>, <second>, <third>, ...)
         * */
        visit(ir.receiver());
        builder.append(".");
        builder.append(ir.name());
        builder.append("(");
        for(int i = 0; i < ir.arguments().size(); i++){
            visit(ir.arguments().get(i));
            if(i < ir.arguments().size() - 1)
                builder.append(", ");
        }
        builder.append(")");
        return builder;
    }

    @Override
    public StringBuilder visit(Ir.Expr.ObjectExpr ir) {
        /**
         * new Object() {
         *   <fields...>
         *   <methods...>
         * }
         * */
        builder.append("new Object() {");
        ++indent;
        for(var fields: ir.fields()) {
            newline(indent);
            visit(fields);
        }
        for(var method: ir.methods()) {
            newline(indent);
            visit(method);
        }
        --indent;
        newline(indent);
        builder.append("}");
        return builder;
    }

}
