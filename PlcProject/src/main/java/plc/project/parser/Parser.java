package plc.project.parser;

import plc.project.lexer.Token;

import javax.swing.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

/**
 * This style of parser is called <em>recursive descent</em>. Each rule in our
 * grammar has dedicated function, and references to other rules correspond to
 * calling that function. Recursive rules are therefore supported by actual
 * recursive calls, while operator precedence is encoded via the grammar.
 *
 * <p>The parser has a similar architecture to the lexer, just with
 * {@link Token}s instead of characters. As before, {@link TokenStream#peek} and
 * {@link TokenStream#match} help with traversing the token stream. Instead of
 * emitting tokens, you will instead need to extract the literal value via
 * {@link TokenStream#get} to be added to the relevant AST.
 */
public final class Parser {

    private final TokenStream tokens;

    public Parser(List<Token> tokens) {
        this.tokens = new TokenStream(tokens);
    }

    public Ast.Source parseSource() throws ParseException {
        //source ::= stmt*
        List<Ast.Stmt> statments = new ArrayList<>();
        while(tokens.has(0)){
            statments.add(parseStmt());
        }
        return new Ast.Source(statments);
    }

    public Ast.Stmt parseStmt() throws ParseException {
        //stmt::= let_stmt | def_stmt | if_stmt | for_stmt | return_stmt | expression_or_assignment_stmt
        if (tokens.peek("LET")) {
            return parseLetStmt();
        } else if (tokens.peek("DEF")) {
            return parseDefStmt();
        } else if (tokens.peek("IF")) {
            return parseIfStmt();
        } else if (tokens.peek("FOR")) {
            return parseForStmt();
        } else if (tokens.peek("RETURN")) {
            return parseReturnStmt();
        } else
            return parseExpressionOrAssignmentStmt();
    }

    private Ast.Stmt.Let parseLetStmt() throws ParseException {
        //let_stmt ::= 'LET' identifier ('=' expr)? ';'
        Optional<Ast.Expr> exp = Optional.empty();
        tokens.match("LET");
        if(tokens.match(Token.Type.IDENTIFIER)){
            var name = tokens.get(-1).literal();

            if(!tokens.peek(";")) {
                if (tokens.match("=") && !tokens.peek(";")) {
                    exp = Optional.of(parseExpr());
                } else
                    throw new ParseException("Parser Error in LET statement: Expected \"= exp\" found: "
                            + (tokens.has(0) ? tokens.get(-2).literal() : "Empty" + tokens.get(-1).literal()));
            }

            if (!tokens.match(";")) {
                throw new ParseException("Parser Error in LET statement: Expected ';' found: "
                        + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
            }

            return new Ast.Stmt.Let(name, exp);
        }else{
            throw new ParseException("Parser Error in LET statement: Expected \"Indentifier\" found: "
                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
        }
    }

    private Ast.Stmt.Def parseDefStmt() throws ParseException {
        //def_stmt ::= 'DEF' identifier '(' (identifier (',' identifier)*)? ')' 'DO' stmt* 'END'
        tokens.match("DEF");
        if(tokens.match(Token.Type.IDENTIFIER)){
            var name = tokens.get(-1).literal();
            List<String> args = new ArrayList<>();
            List<Ast.Stmt> statements = new ArrayList<>();

            if(tokens.match("(")) {
                while (!tokens.match(")")) {
                    //Parse an Argument
                    if (tokens.match(Token.Type.IDENTIFIER)) {
                        var agrument = tokens.get(-1).literal();
                        args.add(agrument);
                        //if we Match a ',' and peek for a ')'
                        if (tokens.match(",") && tokens.peek(")")) {
                            //Throw an error
                            throw new ParseException("Parser Error: Expected Argument found: "
                                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));

                        }
                    }
                }
            }else
                //Throw an error
                throw new ParseException("Parser Error: Expected '(' found: "
                        + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));

            if (tokens.match("DO")) {
                while (!tokens.match("END"))
                    statements.add(parseStmt());
            } else
                throw new ParseException("Parser Error in DEF statement: Expected \"DO\" found: "
                        + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));

            return new Ast.Stmt.Def(name, args, statements);
        }else
            throw new ParseException("Parser Error in DEF statement: Expected \"Indentifier\" found: "
                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
    }

    private Ast.Stmt.If parseIfStmt() throws ParseException {
        //if_stmt ::= 'IF' expr 'DO' stmt* ('ELSE' stmt*)? 'END'
        tokens.match("IF");
        if(!tokens.peek("DO")) {
            var exp = parseExpr();
            List<Ast.Stmt> thenbody = new ArrayList<>();
            List<Ast.Stmt> elsebody= new ArrayList<>();
            if(tokens.match("DO")){
                while (!(tokens.match("END") || tokens.peek("ELSE"))) {
                    thenbody.add(parseStmt());
                }
                if(tokens.match("ELSE"))
                    while (!tokens.match("END")) {
                        elsebody.add(parseStmt());
                    }
            }else
                throw new ParseException("Parser Error in IF statement: Expected \"DO\" found: "
                        + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));

            return new Ast.Stmt.If(exp, thenbody, elsebody);
        }else
            throw new ParseException("Parser Error in IF statement: Expected expperssion found: "
                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
    }

    private Ast.Stmt.For parseForStmt() throws ParseException {
        //for_stmt ::= 'FOR' identifier 'IN' expr 'DO' stmt* 'END'
        tokens.match("FOR");
        if(tokens.match(Token.Type.IDENTIFIER)){
           var name = tokens.get(-1).literal();
           List<Ast.Stmt> body = new ArrayList<>();

           if(!tokens.match("IN"))
               throw new ParseException("Parser Error in FOR statement: Expected \"IN\" found: "
                       + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));

           var exp = parseExpr();

            if (tokens.match("DO")) {
                while (!tokens.match("END"))
                    body.add(parseStmt());
            } else
                throw new ParseException("Parser Error in FOR statement: Expected \"DO\" found: "
                        + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));

            return new Ast.Stmt.For(name, exp, body);

        }else
            throw new ParseException("Parser Error in FOR statement: Expected Identifier found: "
                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
    }

    private Ast.Stmt.Return parseReturnStmt() throws ParseException {
        //return_stmt ::= 'RETURN' expr? ';'
        Optional<Ast.Expr> exp = Optional.empty();
        tokens.match("RETURN");
        if(!tokens.peek(";")){
            exp = Optional.of(parseExpr());
        }
        if(!tokens.match(";")){
            throw new ParseException("Parser Error in Return: Expected ';' found: "
                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
        }
        return new Ast.Stmt.Return(exp);
    }

    private Ast.Stmt parseExpressionOrAssignmentStmt() throws ParseException {
        //expression_or_assignment_stmt ::= expr ('=' expr)? ';'
        //Parse Exp
        var exp = parseExpr();

        //Check for equals
        if(tokens.match("=")){
            var expAssgin = parseExpr();
            //Check for semicolon
            if (!tokens.match(";")) {
                throw new ParseException("Parser Error in Assignment statement: Expected ';' found: "
                        + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
            }
            return new Ast.Stmt.Assignment(exp, expAssgin);

        }

        //Check for semicolon
        if (!tokens.match(";")) {
            throw new ParseException("Parser Error in Expression statement: Expected ';' found: "
                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
        }

        return new Ast.Stmt.Expression(exp);
    }

    public Ast.Expr parseExpr() throws ParseException {
        //expr ::= logical_expr
        return parseLogicalExpr();
    }

    private Ast.Expr parseLogicalExpr() throws ParseException {
        //logical_expr ::= comparison_expr (('AND' | 'OR') comparison_expr)*
        var left = parseComparisonExpr();
        //And takes precedence over or
        while(tokens.match("AND") || tokens.match("OR")) {
            var operator = tokens.get(-1).literal();
            var right = parseComparisonExpr();
            left  = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
        //throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseComparisonExpr() throws ParseException {
        //comparison_expr ::= additive_expr (('<' | '<=' | '>' | '>=' | '==' | '!=') additive_expr)*
        var left = parseAdditiveExpr();
        while(tokens.match("<") ||
                tokens.match("<=") ||
                tokens.match(">") ||
                tokens.match(">=") ||
                tokens.match("==") ||
                tokens.match("!=")
        ){
            var operator = tokens.get(-1).literal();
            var right = parseAdditiveExpr();
            left  = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
        //throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseAdditiveExpr() throws ParseException {
        //additive_expr ::= multiplicative_expr (('+' | '-') multiplicative_expr)*
        var left = parseMultiplicativeExpr();
        while(tokens.match("+") ||
                tokens.match("-")
        ){
            var operator = tokens.get(-1).literal();
            var right = parseMultiplicativeExpr();
            left  = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
        //throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseMultiplicativeExpr() throws ParseException {
        //multiplicative_expr ::= secondary_expr (('*' | '/') secondary_expr)*
        var left = parseSecondaryExpr();
        while(tokens.match("*") ||
                tokens.match("/")
        ){
            var operator = tokens.get(-1).literal();
            var right = parseSecondaryExpr();
            left  = new Ast.Expr.Binary(operator, left, right);
        }
        return left;
        //throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parseSecondaryExpr() throws ParseException {
        //secondary_expr ::= primary_expr ('.' identifier ('(' (expr (',' expr)*)? ')')?)*
        //PrimaryExp
        var reciever = parsePrimaryExpr();
        //Add other logic for grammar
        //(. identifier (function)? )*
        while(tokens.match(".")) {
            if(tokens.match(Token.Type.IDENTIFIER)) {
                var name = tokens.get(-1).literal();
                if(tokens.match("(")) {
                   ArrayList<Ast.Expr> args = new ArrayList<>();
                    while (!tokens.match(")")) {
                        //Parse an Argument
                        var agrument = parseExpr();
                        args.add(agrument);
                        //if we Match a ',' and peek for a ')'
                        if(tokens.match(",") && tokens.peek(")")) {
                            //Throw an error
                            throw new ParseException("Parser Error: Expected Argument found: "
                                    + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
                        }
                    }
                    reciever =  new Ast.Expr.Method(reciever, name, args);
                }else
                    reciever =  new Ast.Expr.Property(reciever, name);

            }else{
                throw new ParseException("Expected Identifier: for name of Method or Property"); //TODO
            }
        }
        return reciever;
        //throw new UnsupportedOperationException("TODO"); //TODO
    }

    private Ast.Expr parsePrimaryExpr() throws ParseException {
        //primary_expr ::= literal_expr | group_expr | object_expr | variable_or_function_expr
        //Literal
        if(tokens.peek(Token.Type.INTEGER) ||
                tokens.peek(Token.Type.DECIMAL) ||
                tokens.peek(Token.Type.STRING) ||
                tokens.peek(Token.Type.CHARACTER) ||
                tokens.peek("TRUE") ||
                tokens.peek("FALSE") ||
                tokens.peek("NIL"))
        {
            return parseLiteralExpr();
        }
        //Group Expression
        else if(tokens.peek("(")) {
            return parseGroupExpr();
        }
        //Object Expression
        else if(tokens.peek("OBJECT")) {
            return parseObjectExpr();
        }
        //Variable or Function
        else if (tokens.peek(Token.Type.IDENTIFIER)) {
            return parseVariableOrFunctionExpr();
        }

        throw new ParseException("Expected an expression but found Token: "
                + (tokens.has(0) ? tokens.get(0).literal() : "Empty")); //TODO: fix throw
        //Add other otpions through switch of if

    }

    private Ast.Expr.Literal parseLiteralExpr() throws ParseException {
        //literal_expr ::= 'NIL' | 'TRUE' | 'FALSE' | integer | decimal | character | string
        if(tokens.match(Token.Type.INTEGER)){
            var literal = tokens.get(-1).literal();
            //Check if the value is a decimal
            double decimalCheck = new BigDecimal(literal).doubleValue();
            if(Math.floor(decimalCheck) == decimalCheck)
                return new Ast.Expr.Literal(
                        new BigDecimal(literal).toBigIntegerExact()
                );
            else
                return new Ast.Expr.Literal(
                       new BigDecimal(literal)
               );
            //Question on bigDecimal?
        }else if(tokens.match(Token.Type.DECIMAL)){
            var literal = tokens.get(-1).literal();
            return new Ast.Expr.Literal(
                    new BigDecimal(literal)
            );
        }else if(tokens.match(Token.Type.STRING)){
            var string = tokens.get(-1).literal();
            string = string.substring(1, string.length() - 1);
            //\b\n\r\t
            //Race Conditions for replace

            // First, replace double backslashes (\\) with a placeholder to avoid conflicts
            string = string.replace("\\\\", "replace_");

            // Now replace all other escape sequences when not preceded by an even number of backslashes
            string = string.replace("\\b", "\b");
            string = string.replace("\\n", "\n");
            string = string.replace("\\r", "\r");
            string = string.replace("\\t", "\t");
            string = string.replace("\\'", "'");
            string = string.replace("\\\"", "\"");

              string = string.replace("replace_", "\\");
            return new Ast.Expr.Literal(
                    string
            );
        }else if(tokens.match(Token.Type.CHARACTER)){
            var c = tokens.get(-1).literal();
            //bnrt'"\
            c = c.replace("\\b", "\b");
            c = c.replace("\\n", "\n");
            c = c.replace("\\r", "\r");
            c = c.replace("\\t",  "\t");
            c = c.replace("\\'", "'");
            c = c.replace("\\\"", "\"");
            c = c.replace("\\\\", "\\");
            return new Ast.Expr.Literal(
                    c.charAt(1)
            );

        } else if (tokens.peek(Token.Type.IDENTIFIER)) {
            if(tokens.get(0).literal().equals("TRUE") || tokens.get(0).literal().equals("FALSE")){
                tokens.match(Token.Type.IDENTIFIER);
                return new Ast.Expr.Literal(
                        //Case-insensitive boolean paring
                        Boolean.parseBoolean(tokens.get(-1).literal())
                );
            }
            if(tokens.get(0).literal().equals("NIL")){
                tokens.match(Token.Type.IDENTIFIER);
                return new Ast.Expr.Literal(null);
            }
        }
        throw new ParseException("Not a literal: " + tokens.get(0).literal());
    }

    private Ast.Expr.Group parseGroupExpr() throws ParseException {
        //group_expr ::= '(' expr')'
        tokens.match("(");
        var exp = parseExpr();
        if(tokens.match(")")){
            return new Ast.Expr.Group(exp);
        }else
            throw new ParseException("Missing ')'");
    }

    private Ast.Expr.ObjectExpr parseObjectExpr() throws ParseException {
        //object_expr ::= 'OBJECT' identifier? 'DO' let_stmt* def_stmt* 'END'
        tokens.match("OBJECT");
        Optional<String> name = Optional.empty();
        if(!tokens.peek("DO")) {
            tokens.match(Token.Type.IDENTIFIER);
            name = Optional.of(tokens.get(-1).literal());
        }
        if(tokens.match("DO")){
            List<Ast.Stmt.Let> fields = new ArrayList<>();
            List<Ast.Stmt.Def> methods = new ArrayList<>();
            while(tokens.match("LET"))
                fields.add(parseLetStmt());
            while(tokens.match("DEF"))
                methods.add(parseDefStmt());
            if(tokens.match("END"))
                return new Ast.Expr.ObjectExpr(name, fields, methods);
            else
                throw new ParseException("Expected an \"END\" for OBJECT Expression found: "
                        + (tokens.has(0) ? tokens.get(0).literal() : "Empty"));
        }else
            throw new ParseException("Expected a \"DO\" for OBJECT Expression found: "
                    + (tokens.has(0) ? tokens.get(0).literal() : "Empty"));
    }

    private Ast.Expr parseVariableOrFunctionExpr() throws ParseException {
        //variable_or_function_expr ::= identifier ('(' (expr (',' expr)*)? ')')?

        //Variable name is an identifier
        tokens.match(Token.Type.IDENTIFIER);
        //Previously matched token
        var name = tokens.get(-1).literal();
        ArrayList<Ast.Expr> args = new ArrayList<>();
        if(tokens.match("(")) {
            while (!tokens.match(")")) {
                //Parse an Argument
                var agrument = parseExpr();
                args.add(agrument);
                //if we Match a ',' and peek for a ')'
                if(tokens.match(",") && tokens.peek(")")) {
                    //Throw an error
                    throw new ParseException("Parser Error: Expected Argument found: "
                            + (tokens.has(0) ? tokens.get(-1).literal() : "Empty"));
                }
            }
            return new Ast.Expr.Function(name, args);
        }
        return new Ast.Expr.Variable(name);
    }

    private static final class TokenStream {

        private final List<Token> tokens;
        private int index = 0;

        private TokenStream(List<Token> tokens) {
            this.tokens = tokens;
        }

        /**
         * Returns true if there is a token at (index + offset).
         */
        public boolean has(int offset) {
            return index + offset < tokens.size();
        }

        /**
         * Returns the token at (index + offset).
         */
        public Token get(int offset) {
            checkState(has(offset));
            return tokens.get(index + offset);
        }

        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is either a {@link Token.Type}, matching tokens
         * of that type, or a {@link String}, matching tokens with that literal.
         * In effect, {@code new Token(Token.Type.IDENTIFIER, "literal")} is
         * matched by both {@code peek(Token.Type.IDENTIFIER)} and
         * {@code peek("literal")}.
         */
        public boolean peek(Object... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var token = tokens.get(index + offset);
                var pattern = patterns[offset];
                checkState(pattern instanceof Token.Type || pattern instanceof String, pattern);
                if (!token.type().equals(pattern) && !token.literal().equals(pattern)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the token stream.
         */
        public boolean match(Object... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
            }
            return peek;
        }
    }
}
