package plc.project.evaluator;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;

import java.math.BigInteger;
import java.text.RuleBasedCollator;
import java.util.*;

public final class Environment {

    public static Scope scope() {
        var scope = new Scope(null);
        //"Native" functions for printing and creating lists.
        scope.define("debug", new RuntimeValue.Function("debug", Environment::debug));
        scope.define("print", new RuntimeValue.Function("print", Environment::print));
        scope.define("log", new RuntimeValue.Function("log", Environment::log));
        scope.define("list", new RuntimeValue.Function("list", Environment::list));
        scope.define("range", new RuntimeValue.Function("range", Environment::range));
        //Helper functions for testing variables, functions, and objects.
        scope.define("variable", new RuntimeValue.Primitive("variable"));
        scope.define("function", new RuntimeValue.Function("function", Environment::function));
        var object = new RuntimeValue.ObjectValue(Optional.of("Object"), new Scope(null));
        scope.define("object", object);
        object.scope().define("property", new RuntimeValue.Primitive("property"));
        object.scope().define("method", new RuntimeValue.Function("method", Environment::method));
        return scope;
    }

    /**
     * Prints the raw RuntimeValue.toString() result.
     */
    private static RuntimeValue debug(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected debug to be called with 1 argument.");
        }
        System.out.println(arguments.getFirst());
        return new RuntimeValue.Primitive(null);
    }

    /**
     * Prints a formatted RuntimeValue.
     */
    private static RuntimeValue print(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected print to be called with 1 argument.");
        }
        System.out.println(arguments.getFirst().print());
        return new RuntimeValue.Primitive(null);
    }

    /**
     * Prints a formatted RuntimeValue and returns it.
     */
    static RuntimeValue log(List<RuntimeValue> arguments) throws EvaluateException {
        if (arguments.size() != 1) {
            throw new EvaluateException("Expected log to be called with 1 argument.");
        }
        System.out.println("log: " + arguments.getFirst().print());
        return arguments.getFirst(); //size validated by print
    }

    /**
     * Returns a List value containing all arguments.
     */
    private static RuntimeValue list(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    /**
     * Takes two integer arguments (start, end) and returns a List containing
     * all integers in that range (inclusive, exclusive).
     */
    private static RuntimeValue range(List<RuntimeValue> arguments) throws EvaluateException {
        if(arguments.size() == 2 &&
                arguments.get(0) instanceof RuntimeValue.Primitive primMin &&
                arguments.get(1) instanceof RuntimeValue.Primitive primMax &&
                primMax.value() instanceof BigInteger max &&
                primMin.value() instanceof BigInteger min) {
            List<RuntimeValue> range = new ArrayList<>();
            for (var i = min.intValueExact(); i <= max.intValueExact(); i++) {
                range.add(new RuntimeValue.Primitive(min));
                min = min.add(BigInteger.ONE);
            }
            return new RuntimeValue.Primitive(List.of(range.toArray()));
        }else
            throw new EvaluateException("Expected range to be called with 2 Integer arguments.");
    }

    /**
     * Returns a list of all function arguments.
     */
    private static RuntimeValue function(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments);
    }

    /**
     * Returns a list of all method arguments. Question: why the difference?
     */
    private static RuntimeValue method(List<RuntimeValue> arguments) {
        return new RuntimeValue.Primitive(arguments.subList(1, arguments.size()));
    }

}
