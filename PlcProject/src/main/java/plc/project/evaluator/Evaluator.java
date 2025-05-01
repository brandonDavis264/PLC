package plc.project.evaluator;

import com.google.common.collect.ImmutableCollection;
import plc.project.parser.Ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Consumer;

public final class Evaluator implements Ast.Visitor<RuntimeValue, EvaluateException> {

    private Scope scope;

    public Evaluator(Scope scope) {
        this.scope = scope;
    }

    @Override
    public RuntimeValue visit(Ast.Source ast) throws EvaluateException {
        System.out.println("Starting Evaluation In Scope: " + this.scope);
        RuntimeValue value = new RuntimeValue.Primitive(null);
        try {
            for (var stmt : ast.statements()) {
                value = visit(stmt);
            }
        //Handle the possibility of RETURN being called outside of a function.
        }catch (Return e){
            throw new EvaluateException("Cannot Return Outside of Function Definition");
        }
        return value;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Let ast) throws EvaluateException {
        /**
         * Defines a variable in the current scope, with the following behavior:
         * 1. Ensure name is not already defined in the current scope.
         * 2. Define name in the current scope with an initial value of the result
         *      of evaluating value, if present, or else NIL.
         * 3. Return the value of the defined variable, for REPL/testing.
         */
        // 1. Ensure name is not already defined in the current scope.
        if(scope.get(ast.name(),true).isPresent()) {
            throw new EvaluateException("Variable already Defined: " + ast.name() + ".");
        }else {
           //2. Define name in the current scope with an initial value of the result
                // of evaluating value
            //if present, or else NIL.
            var assignment = ast.value();
            if(assignment.isPresent()) {
                var result = requireType(visit(assignment.get()), RuntimeValue.class);
                scope.define(ast.name(), result);
                //3. Return the value of the defined variable, for REPL/testing.
                return result;
            }else{
                var result =  new RuntimeValue.Primitive(null);
                scope.define(ast.name(), result);
                //3. Return the value of the defined variable, for REPL/testing.
                return result;
            }
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Def ast) throws EvaluateException {
        /**
         * 	Defines a function in the current scope, with the following behavior:
         *      1. Ensure name is not already defined in the current scope.
         *      2. Ensure names in parameters are unique.
         *      3. Define name in the current scope with the value being a RuntimeValue.
         *
         *          Function with the name and following definition:
         *              A) Within a new scope that is a child of the scope where the function
         *                  was defined (i.e., static scoping):
         *                      i) Define variables for all parameters to the values in arguments,
         *                          ensuring the correct number of arguments is passed.
         *                      ii) Evaluate the body statements sequentially.
         *              B) Return the value resulting from RETURN, if present, or else NIL.
         *              c) Ensure the current scope is restored to the original scope, even
         *                  if an exception is thrown.
         *
         *      4. Return the value of the defined function, for REPL/testing.
         */
        // 1. Ensure name is not already defined in the current scope.
        if(scope.get(ast.name(),true).isPresent()) {
            throw new EvaluateException("Function Name already Defined Somewhere Else: " + ast.name() + "().");
        }
        // 2. Ensure names in parameters are unique.
        Set<String> uniqueNames = new HashSet<>();
        for(var args : ast.parameters()){
            if(!uniqueNames.add(args))
                throw new EvaluateException("Function Definition Does not have unique parameters: " + ast.name() + "().");
        }
        //Save the Scope where the function was defined NOT INVOKED
        Scope definitionScope = scope;
        // 3. Make a lambda Function Definition to be called/invoked
        var function = new RuntimeValue.Function(ast.name(), (lambdaArguments) ->{
            // Within a new scope that is a child of the scope where the function
                // was defined
            Scope prevScope = definitionScope;
            Scope functionScope = new Scope(definitionScope);
            scope = functionScope;
            // Define variables for all parameters to the values in arguments,
                // ensuring the correct number of arguments is passed.
            if(lambdaArguments.size() == ast.parameters().size()) {
                for (int i = 0; i < ast.parameters().size(); ++i) {
                    String name = ast.parameters().get(i);
                    scope.define(name, lambdaArguments.get(i));
                }
            }else
                throw new EvaluateException("Function Requires: " + ast.parameters().size() + " Parameter(s)");
            
            RuntimeValue returnStatement = new RuntimeValue.Primitive(null);
            // Evaluate the body statements sequentially.
            try {
                // Evaluate the body statements sequentially.
                try {
                    for (var statement : ast.body()) {
                        visit(statement);
                    }
                }catch (Return returnValue){
                    returnStatement = returnValue.value;
                }
            } finally {
                System.out.println("\nIn Scope: " + scope.toString() + ".\nRestoring to: " + prevScope + ".\n");
                scope = prevScope;
            }
            // Return the value resulting from RETURN, if present, or else NIL.
            //scope = currScope;
            return returnStatement;
        });

        // 4. Define name in the current scope with the value being a RuntimeValue.
        scope.define(ast.name(), function);

        // 5. Return the value of the defined function, for REPL/testing.
        return function;
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.If ast) throws EvaluateException {
        /**
         * Evaluates an IF statement, with the following behavior:
         *      1. Evaluate condition and ensure it is a Boolean.
         *      2. Within a new scope:
         *          a) If condition is TRUE, evaluate thenBody statements.
         *          b) Otherwise (when FALSE), evaluate elseBody statements.
         *      3. Ensure the current scope is restored to the original scope,
         *          even if an exception is thrown.
         *      4. Return the value of the last statement executed, or else NIL
         *          if empty, for REPL/testing.
         */
        // 1. Evaluate condition and ensure it is a Boolean.
        var bool = visit(ast.condition());
        var primativeBool = requireType(bool, RuntimeValue.Primitive.class).value();
        if(primativeBool instanceof Boolean){
            // 2. Within a new scope:
            Scope currScope = scope;
            Scope ifScope = new Scope(scope);
            scope = ifScope;
            RuntimeValue lastStatement = new RuntimeValue.Primitive(null);
            try {
                if ((Boolean) primativeBool) {
                    // a) If condition is TRUE, evaluate thenBody statements.
                    for (var statement : ast.thenBody()) {
                        lastStatement = visit(statement);
                    }
                } else {
                    // b) If condition is FALSE, evaluate elseBody statements.
                    for (var statement : ast.elseBody()) {
                        lastStatement = visit(statement);
                    }
                }
            }finally {
                System.out.println("\nIn Scope: " + scope.toString() + ".\nRestoring to: " + currScope + ".\n");
                scope = currScope;
            }
            return lastStatement;
        }else {
            throw new EvaluateException("Not a Boolean to Evaluate in If-Conditions: " + primativeBool);
        }
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.For ast) throws EvaluateException {
        /**
         * Evaluates a FOR loop, with the following behavior:
         * 	1. Evaluate expression and ensure it is an Iterable.
         * 	2. For each element in expression, each within their own new scope:
         * 		1. Verify that the element is a RuntimeValue
         * 	       	(it should be, but we have to convince Java of that and
         * 	       	there could also be bugs).
         * 		2. Define a variable name with the value of the element.
         * 		3. Evaluate body statements sequentially.
         * 	3. Return NIL.
         * Note: The provided list/range functions can be used for testing.
         */
        // 1. Evaluate expression and ensure it is an Iterable.
        var expr = visit(ast.expression());
        final var iterable = requireType(expr, Iterable.class);
        // 2. For each element in expression, each within their own new scope.
            // Element refers to each element in the iterable expression
        for(var element : iterable){
            // Create an element's scope
            Scope currScope = scope;
            Scope elementScope = new Scope(scope);
            scope = elementScope;
            try {
                // Verify that the element is a RuntimeValue
                if(!(element instanceof RuntimeValue)){
                    throw new EvaluateException("Not a RuntimeValue");
                }

                // Define a variable name with the value of the element.
                scope.define(ast.name(), (RuntimeValue) element);
                // Evaluate body statements sequentially.
                for (var statement : ast.body()) {
                    visit(statement);
                }
            }finally {
                System.out.println("\nIn Scope: " + scope.toString() + ".\nRestoring to: " + currScope + ".\n");
                scope = currScope;
            }
        }

        // 3. Return NIL.
        return new RuntimeValue.Primitive(null);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Return ast) throws EvaluateException {
        /**
         * 	Evaluates a RETURN statement, with the following behavior:
         *      1. Throws an exception to return the evaluated value (or else NIL),
         *          which will be handled by a surrounding function or else Source.
         *              - Hint: See "Return Statements" Links to an external site.in Crafting
         *                  Interpreters.
         *      2. Does not return from visit, since RETURN should change control flow.
         */
        // Throws an exception to return the evaluated value (or else NIL)
        RuntimeValue value = null;
        if (ast.value().isPresent())
            value = visit(ast.value().get());
        else
            value = new RuntimeValue.Primitive(null);
        // This Statement Will be brought up to the function definition
        throw new Return(value);
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Expression ast) throws EvaluateException {
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Stmt.Assignment ast) throws EvaluateException {
        /**
         * Evaluates an assignment statement, with the following behavior:
         *      1. Ensure that the expression is either a variable/property,
         *              as any other expression type cannot be assigned to.
         *      2. For variables, ensure that the variable name is defined
         *              and set the value of the variable to the evaluated
         *              value.
         *      3. For properties, ensure that the variable property is defined
         *              on the receiver and set the value of the variable to the
         *              evaluated value.
         *      4. Return the value of the variable / method invocation,
         *              for REPL/testing.
         */
        if(ast.expression() instanceof Ast.Expr.Variable || ast.expression() instanceof Ast.Expr.Property) {
            if(ast.expression() instanceof Ast.Expr.Variable) {
                // Ensure that the variable name is defined
                var variable = visit(ast.expression());
                // Set the value of the variable to the evaluated value
                var assignment = visit(ast.value());
                scope.set(((Ast.Expr.Variable) ast.expression()).name(), assignment);
                // Return the value of the variable
                return assignment;
            }else {
                // Ensure that the variable property is defined
                //     (ON THE RECEIVER SIDE)
                var receiver = visit(((Ast.Expr.Property) ast.expression()).receiver());
                // Check if the reciever
                var check_if_defined = ((RuntimeValue.ObjectValue) receiver)
                        .scope()
                        .get(((Ast.Expr.Property) ast.expression())
                                .name(),
                                false);
                if(check_if_defined.isEmpty()){
                    throw new EvaluateException("Undefined Variable: " + ((Ast.Expr.Property) ast.expression()).name() + ".");
                }

                // set the value of the variable to the evaluated value.
                var assignment = visit(ast.value());
                ((RuntimeValue.ObjectValue) receiver)
                        .scope()
                        .set(((Ast.Expr.Property) ast.expression())
                                .name(),
                                assignment);

                //Return the value of the method invocation
                return assignment;
            }
            // 4. Return the value of the variable / method invocation.
        }else
            throw new EvaluateException("Not a Variable or Property for assignmnet: " + ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Literal ast) throws EvaluateException {
        return new RuntimeValue.Primitive(ast.value());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Group ast) throws EvaluateException {
        //Evaluates and returns the containing expression
        return visit(ast.expression());
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Binary ast) throws EvaluateException {
        /*
            Both operators will be evaluated left to right before any validation,
            with the exception of AND/OR due to short-circuiting.
        */
        //Do different things depending on the operator
        switch (ast.operator()) {
            /**
                +: If either operand is a String, the result is their concatenation.
                Else, the left operand must be a BigInteger/BigDecimal, the right
                operand must be the same type as the left operand, and the result is
                addition.
             */
            case "+": {
                // Visit
                var left = visit(ast.left());
                var right = visit(ast.right());
                //Get their Values
                var runtimeLeft = requireType(left, RuntimeValue.class);
                var runtimeRight = requireType(right, RuntimeValue.class);

                if((runtimeLeft instanceof RuntimeValue.Primitive lPrim
                        && lPrim.value() instanceof String)||
                        (runtimeRight instanceof RuntimeValue.Primitive rPim &&
                                rPim.value() instanceof String)){

                        return new RuntimeValue.Primitive(runtimeLeft.print() + runtimeRight.print());

                }else if((runtimeLeft instanceof RuntimeValue.Primitive lPrim
                        && lPrim.value() instanceof BigDecimal) &&
                        (runtimeRight instanceof RuntimeValue.Primitive rPim &&
                                rPim.value() instanceof BigDecimal)){

                    // Check if BigDec
                    var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                    var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                    return new RuntimeValue.Primitive((((BigDecimal) primativeLeft).add((BigDecimal) primativeRight)));

                }else if((runtimeLeft instanceof RuntimeValue.Primitive lPrim
                        && lPrim.value() instanceof BigInteger) &&
                        (runtimeRight instanceof RuntimeValue.Primitive rPim &&
                                rPim.value() instanceof BigInteger)) {

                    // Check if BigInt
                    var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                    var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                    return new RuntimeValue.Primitive((((BigInteger) primativeLeft).add((BigInteger) primativeRight)));
                }else
                    // else inccorrect types for addition
                    throw new EvaluateException("Evaluator Error: Incorrect matching types for Addition\nLeft Operation: " + runtimeLeft + "\nRight Operation: " + runtimeRight);

            }
            /**
                 -/*: The left operand must be a BigInteger/BigDecimal,
                 the right operand must be the same type as the left
                 operand, and the result is their subtraction/multiplication.
             */
            case "-": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                //Check if the type is a Decimal
                if(primativeRight instanceof BigDecimal && primativeLeft instanceof BigDecimal){
                    return new RuntimeValue.Primitive((((BigDecimal) primativeLeft).subtract((BigDecimal) primativeRight)));
                }else if(primativeLeft instanceof BigInteger && primativeRight instanceof BigInteger) {
                    //if the type is Integer
                    return new RuntimeValue.Primitive((((BigInteger) primativeLeft).subtract((BigInteger) primativeRight)));
                }else
                    throw new EvaluateException("Evaluator Error: Not a valid number to subtract");
            } case "*": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                //Check if the type is a Decimal
                if(primativeRight instanceof BigDecimal && primativeLeft instanceof BigDecimal){
                    return new RuntimeValue.Primitive((((BigDecimal) primativeLeft).multiply((BigDecimal) primativeRight)));
                }else if(primativeLeft instanceof BigInteger && primativeRight instanceof BigInteger) {
                    //if the type is Integer
                    return new RuntimeValue.Primitive((((BigInteger) primativeLeft).multiply((BigInteger) primativeRight)));
                }else
                    throw new EvaluateException("Evaluator Error: Not a valid number to Multiply");
            }
            /**
                 /: The left operand must be a BigInteger/BigDecimal, the right operand must be the same type as the left operand, and the result depends on the type/values:
                     BigInteger will use floor division, like regular integers.
                     BigDecimal will require a rounding mode, which will be HALF_EVEN. This rounds midpoints to the nearest even value (1.5,2.5 -> 2.0).
                     In both cases, divide by zero must be handled and throw our error.
             */
            case "/":{
                var left = visit(ast.left());
                var right = visit(ast.right());

                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();


                if(primativeRight instanceof BigDecimal){
                    if(((BigDecimal) primativeRight).compareTo(BigDecimal.ZERO) == 0)
                        throw new EvaluateException("Evaluator Error: Division by zero is not allowed.");
                    else {
                        if(primativeLeft instanceof BigDecimal) {
                            var val = ((BigDecimal) primativeLeft).divide((BigDecimal) primativeRight, RoundingMode.HALF_EVEN);
                            return new RuntimeValue.Primitive(val);
                        }else
                            throw new EvaluateException("Evaluator Error: Not a valid number to Devide");
                    }
                }else if(primativeRight instanceof BigInteger) {
                    if(((BigInteger) primativeRight).compareTo(BigInteger.ZERO) == 0)
                        throw new EvaluateException("Evaluator Error: Division by zero is not allowed.");
                    else {
                        //if the type is Integer
                        if (primativeLeft instanceof BigInteger) {
                            var val = ((BigInteger) primativeLeft).divide((BigInteger) primativeRight);
                            return new RuntimeValue.Primitive(val);
                        } else
                            throw new EvaluateException("Evaluator Error: Not a valid number to Devide");
                    }
                }else
                    throw new EvaluateException("Evaluator Error: Not a valid number to Devide");
            }
            /**
                ==/!=: Tests for equality using Objects.equals
                (note this is not the standard equals method -
                check the Javadocs for details).
             */
            case "==":{
                var left = visit(ast.left());
                var right = visit(ast.right());

                if(left instanceof RuntimeValue.Primitive && right instanceof RuntimeValue.Primitive) {
                    var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                    var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                    return new RuntimeValue.Primitive(Objects.equals(primativeLeft, primativeRight));
                }else if(left instanceof RuntimeValue.ObjectValue && right instanceof RuntimeValue.ObjectValue) {
                    var objLeft = requireType(left, RuntimeValue.ObjectValue.class);
                    var objRight = requireType(right, RuntimeValue.ObjectValue.class);
                    return new RuntimeValue.Primitive(Objects.equals(objLeft, objRight));
                }
            }
            case "!=":{
                var left = visit(ast.left());
                var right = visit(ast.right());

                if(left instanceof RuntimeValue.Primitive && right instanceof RuntimeValue.Primitive) {
                    var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                    var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                    return new RuntimeValue.Primitive(!Objects.equals(primativeLeft, primativeRight));
                }else if(left instanceof RuntimeValue.ObjectValue && right instanceof RuntimeValue.ObjectValue) {
                    var objLeft = requireType(left, RuntimeValue.ObjectValue.class);
                    var objRight = requireType(right, RuntimeValue.ObjectValue.class);
                    return new RuntimeValue.Primitive(!Objects.equals(objLeft, objRight));
                }


            }
            /**
                </<=/>/>=: The left operand must be Comparable,
                and the right operand must be same type as the left
                operand. The result is TRUE/FALSE based on the specific
                operator, which you will have to interpret using
                Comparable (check the Javadocs).
             */
            case "<": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();

                //Must be "Comparable"
                if(primativeRight instanceof Comparable && primativeLeft instanceof Comparable){
                    //Must be the same type
                    if(primativeLeft.getClass().equals(primativeRight.getClass())){
                        var result = ((Comparable<Object>) primativeLeft).compareTo(primativeRight) < 0;
                        return new RuntimeValue.Primitive(result);
                    }else
                        throw new EvaluateException("Evaluator Error: Both Types are not the same.");
                }else
                    throw new EvaluateException("Evaluator Error: Objects not comparable");
            }
            case "<=": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();

                //Must be "Comparable"
                if(primativeRight instanceof Comparable && primativeLeft instanceof Comparable){
                    //Must be the same type
                    if(primativeLeft.getClass().equals(primativeRight.getClass())){
                        var result = ((Comparable<Object>) primativeLeft).compareTo(primativeRight) <= 0;
                        return new RuntimeValue.Primitive(result);
                    }else
                        throw new EvaluateException("Evaluator Error: Both Types are not the same.");
                }else
                    throw new EvaluateException("Evaluator Error: Objects not comparable");
            }
            case ">": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();

                //Must be "Comparable"
                if(primativeRight instanceof Comparable && primativeLeft instanceof Comparable){
                    //Must be the same type
                    if(primativeLeft.getClass().equals(primativeRight.getClass())){
                        var result = ((Comparable<Object>) primativeLeft).compareTo(primativeRight) > 0;
                        return new RuntimeValue.Primitive(result);
                    }else
                        throw new EvaluateException("Evaluator Error: Both Types are not the same.");
                }else
                    throw new EvaluateException("Evaluator Error: Objects not comparable");
            }
            case ">=": {
                var left = visit(ast.left());
                var right = visit(ast.right());

                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();

                //Must be "Comparable"
                if(primativeRight instanceof Comparable && primativeLeft instanceof Comparable){
                    //Must be the same type
                    if(primativeLeft.getClass().equals(primativeRight.getClass())){
                        var result = ((Comparable<Object>) primativeLeft).compareTo(primativeRight) >= 0;
                        return new RuntimeValue.Primitive(result);
                    }else
                        throw new EvaluateException("Evaluator Error: Both Types are not the same.");
                }else
                    throw new EvaluateException("Evaluator Error: Objects not comparable");
            }
            /**
                 AND/OR: Operands must be Booleans. AND/OR have
                 their standard logical meanings and use short-circuiting,
                 as in Java.
             */
            case "AND":{
                //Short Circuit
                var left = visit(ast.left());
                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                if(primativeLeft instanceof Boolean) {
                    if (!(Boolean) primativeLeft) {
                        return new RuntimeValue.Primitive(false);
                    }
                }else
                    throw new EvaluateException("Evaluator Error: Not a Boolean");

                //Evaluate
                var right = visit(ast.right());
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                if(primativeRight instanceof Boolean){
                    var result = (Boolean)primativeLeft && (Boolean)primativeRight;
                    return new RuntimeValue.Primitive(result);
                }else{
                    throw new EvaluateException("Evaluator Error: Not a Boolean");
                }
            } case "OR":{
                //Short Circuit
                var left = visit(ast.left());
                var primativeLeft = requireType(left, RuntimeValue.Primitive.class).value();
                if(primativeLeft instanceof Boolean) {
                    if ((Boolean) primativeLeft) {
                        return new RuntimeValue.Primitive(true);
                    }
                }else
                    throw new EvaluateException("Evaluator Error: Not a Boolean");

                //Evaluate
                var right = visit(ast.right());
                var primativeRight = requireType(right, RuntimeValue.Primitive.class).value();
                if(primativeRight instanceof Boolean){
                    var result = (Boolean)primativeLeft || (Boolean)primativeRight;
                    return new RuntimeValue.Primitive(result);
                }else{
                    throw new EvaluateException("Evaluator Error: Not a Boolean");
                }
            }
            default:
                throw new EvaluateException("Evaluated Error: Unsupported Operation");
        }
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Variable ast) throws EvaluateException {
        //Ensures that the variable name is defined and returns it's value
        var binding = scope.get(ast.name(),false);
        return binding.orElseThrow(() -> new EvaluateException("Undefined Variable: " + ast.name() + "."));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Property ast) throws EvaluateException {
        /**
         * Evaluates a property, with the following behavior:
         *  1. Evaluate the receiver and ensure it is a RuntimeValue.Object.
         *  2. Ensure that the variable name is defined by the receiver.
         *  3. Return the variable value.
         */
        // 1. Evaluate the receiver and ensure it is a RuntimeValue.Object.
        var receiver = visit(ast.receiver());
        var objectValReceiver = requireType(receiver, RuntimeValue.ObjectValue.class);
        // 2. Ensure that the variable name is defined by the receiver.
            // Retrieve the property from the receiver’s scope, not the global scope
        var binding = objectValReceiver.scope().get(ast.name(),true);
        // 3. Return the variable value.
        return binding.orElseThrow(() -> new EvaluateException("Undefined Variable: " + ast.name() + "."));
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Function ast) throws EvaluateException {
        /**
         *   Evaluates a function, with the following behavior:
         *         1. Ensure that the function name is defined and that the value is actually
         *              a RuntimeValue.Function.
         *         2. Evaluate all arguments sequentially.
         *         3. Return the result of invoking the function with the evaluated arguments.
         */
        List<RuntimeValue> args = new ArrayList<>();

        //1a) Ensure function name is defined
        var binding = scope.get(ast.name(),false)
                .orElseThrow(() -> new EvaluateException("Undefined Function: " + ast.name() + "."));
        //1b) That the value is actually a RuntimeValue.Function.
        if(binding instanceof RuntimeValue.Function function){
            //2) Evaluate all arguments sequentially.
            for(var arg: ast.arguments()){
                args.add(visit(arg));
            }
            //3) Return the result of invoking the function with the evaluated arguments.
            return function.definition().invoke(args);
        }else
            throw new EvaluateException("Not a Function: " + ast.name() + ".");
    }

    @Override
    public RuntimeValue visit(Ast.Expr.Method ast) throws EvaluateException {
        /**
         * Evaluates a method, with the following behavior:
         *          1. Evaluate the receiver and ensure it is a RuntimeValue.Object.
         *          2. Ensure that the method name is defined by the receiver and that the value is
         *                  actually a RuntimeValue.Function.
         *          3. Evaluate all arguments sequentially.
         *          4. Return the result of invoking the method with the first argument being the
         *                  receiver followed by any explicit arguments.
         *                          - This is a common calling convention for methods.
         */
        // 1. Evaluate the receiver and ensure it is a RuntimeValue.Object.
        var receiver = visit(ast.receiver());
        var objectValReceiver = requireType(receiver, RuntimeValue.ObjectValue.class);
        // 2. Ensure that the method name is defined by the receiver
        var binding = objectValReceiver.scope().get(ast.name(),false)
                .orElseThrow(() -> new EvaluateException("Undefined Function: " + ast.name() + "."));;
        // Ensure the value is actually a RuntimeValue.Function.
        if(binding instanceof RuntimeValue.Function function){
            // 3. Evaluate all arguments sequentially.
            List<RuntimeValue> args = new ArrayList<>();
            args.add(receiver);
            for(var arg: ast.arguments()){
                args.add(visit(arg));
            }
            return function.definition().invoke(args);
        }else
            throw new EvaluateException("Not a Function: " + ast.name() + "."); //TODO
    }

    @Override
    public RuntimeValue visit(Ast.Expr.ObjectExpr ast) throws EvaluateException {
        /**
         * Creates and returns an ObjectValue, with the following behavior:
         *      1. The name is the same as the AST.
         *      2. The scope is a new child of the current scope.
         *      3. For all fields (see also Stmt.Let):
         *          A) Ensure field.name is not already defined in the object's scope.
         * 	        B) Define field.name in the object's scope with an
         * 	            initial value of the result of evaluating field.value,
         * 	            if present, or else NIL.
         *      4. For all methods (see also Stmt.Def):
         * 	        A) Ensure method.name is not already defined in the object's scope.
         * 	        B) Ensure names in method.parameters are unique.
         * 	        C) Define method.name in the current scope with the value being a
         * 	            RuntimeValue.Function with the name and following definition:
         * 		            I. Within a new scope that is a child of the scope where
         * 		                the object was defined (i.e., static scoping):
         * 			                a) Define the variable this to be the method receiver,
         * 			                    which is passed as the first argument.
         * 				                    - Do NOT use the object directly
         * 				                    - this would not support inheritance and
         * 				                        thus limits dynamic dispatch!
         * 			                b) Define variables for all parameters to the corresponding
         * 			                    values in arguments, ensuring the correct number
         * 			                    of arguments is passed.
         * 			                c) Evaluate the body statements sequentially.
         * 		                    d) Return the value resulting from RETURN,
         * 		                    if present, or else NIL.
         * 		    E) Ensure the current scope is restored to the original scope,
         * 		        even if an exception is thrown.
         */

        // Creat an Object value with:
            //1. The name is the same as the AST.
            //2. The scope is a new child of the current scope.
        Scope currScope = scope;
        Scope objScope = new Scope(scope);
        scope = objScope;

        RuntimeValue.ObjectValue obj = new RuntimeValue.ObjectValue(ast.name(), objScope);
        try {
            // For Each Object feild:
            for (var feild : ast.fields()) {
                // Ensure field.name is not already defined in the object's scope.
                if (scope.get(feild.name(), true).isPresent())
                    throw new EvaluateException("Variable Name already Defined Somewhere Else: " + ast.name() + "().");
                // Define field.name in the object's scope with an
                //initial value of the result of evaluating field.value,
                //if present, or else NIL.
                if (feild.value().isPresent())
                    scope.define(feild.name(), visit(feild.value().get()));
                else
                    scope.define(feild.name(), new RuntimeValue.Primitive(null));
            }

            // For Each Object Method:
            for (var method : ast.methods()) {
                // Ensure method.name is not already defined in the object's scope.
                if (scope.get(method.name(), true).isPresent())
                    throw new EvaluateException("Function Name already Defined Somewhere Else: " + ast.name() + "().");
                // Ensure names in method.parameters are unique.
                Set<String> uniqueParameters = new HashSet<>();
                for (var parameter : method.parameters()) {
                    if (!uniqueParameters.add(parameter))
                        throw new EvaluateException("Function Definition Does not have unique parameters: " + ast.name() + "().");
                }
                //Define method.name in the current scope with the value being a
                //RuntimeValue.Function with the name and following definition
                //CREATED --- THIS IS NOT INVOKVED UNTILL CALLED
                var methodDefinition = new RuntimeValue.Function(method.name(), (arguments) -> {
                    // Within a new scope that is a child of the scope where the object
                    // was defined
                    Scope methodScope = new Scope(currScope);
                    Scope prevScope = scope;
                    scope = methodScope;
                    // Define the variable this to be the method receiver,
                    // which is passed as the first argument.
                    scope.define("this", arguments.get(0));

                    // Define variables for all parameters to the corresponding
                    // values in arguments, ensuring the correct number
                    // of arguments is passed.
                    if((method.parameters().size()+1) == arguments.size()) {
                        for (int i = 0; i < method.parameters().size(); i++) {
                            scope.define(method.parameters().get(i), arguments.get(i + 1));
                        }
                    }else
                        throw new EvaluateException("Function Requires: " + method.parameters().size() + " Parameter(s)");
                    // Evaluate the body statements sequentially.
                    RuntimeValue returnStatement = new RuntimeValue.Primitive(null);
                    try {
                        try {
                            for (var statement : method.body()) {
                                visit(statement);
                            }
                        } catch (Return returnedValue) {
                            returnStatement = returnedValue.value;
                        }
                    }finally {
                        System.out.println("\nIn Scope: " + scope.toString() + ".\nRestoring to: " + prevScope + ".\n");
                        scope = prevScope;
                    }
                    // Return the value resulting from RETURN,
                    // if present, or else NIL

                    return returnStatement;
                });
                scope.define(method.name(), methodDefinition);
            }
        } finally {
            System.out.println("\nIn Scope: " + scope.toString() + ".\nRestoring to: " + currScope + ".\n");
            scope = currScope;
        }
        return obj;
    }

    /**
     * Helper function for extracting RuntimeValues of specific types. If the
     * type is subclass of {@link RuntimeValue} the check applies to the value
     * itself, otherwise the value is expected to be a {@link RuntimeValue.Primitive}
     * and the check applies to the primitive value.
     */
    private static <T> T requireType(RuntimeValue value, Class<T> type) throws EvaluateException {
        //To be discussed in lecture 3/5.
        if (RuntimeValue.class.isAssignableFrom(type)) {
            if (!type.isInstance(value)) {
                throw new EvaluateException("Expected value to be of type " + type + ", received " + value.getClass() + ".");
            }
            return (T) value;
        } else {
            var primitive = requireType(value, RuntimeValue.Primitive.class);
            if (!type.isInstance(primitive.value())) {
                var received = primitive.value() != null ? primitive.value().getClass() : null;
                throw new EvaluateException("Expected value to be of type " + type + ", received " + received + ".");
            }
            return (T) primitive.value();
        }
    }

    // For Returning a Value
    class Return extends RuntimeException {
        final RuntimeValue value;
        //Make Return Exception
        Return(RuntimeValue value) {
            super(null, null, false, false);
            this.value = value;
        }
    }
}
