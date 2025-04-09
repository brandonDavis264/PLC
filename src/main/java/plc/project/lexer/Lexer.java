package plc.project.lexer;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkState;

/**
 * The lexer works through a combination of {@link #lex()}, which repeatedly
 * calls {@link #lexToken()} and skips over whitespace/comments, and
 * {@link #lexToken()}, which determines the type of the next token and
 * delegates to the corresponding lex method.
 *
 * <p>Additionally, {@link CharStream} manages the lexer state and contains
 * {@link CharStream#peek} and {@link CharStream#match}. These are helpful
 * utilities for working with character state and building tokens.
 */
public final class Lexer {

    private final CharStream chars;

    public Lexer(String input) {
        chars = new CharStream(input);
    }

    public List<Token> lex() throws LexException {
        //Doing all the lexing operation
        //Repeatly call lex token & skip over whitespace and comments
        ArrayList<Token> tokens = new ArrayList<>();
        //While not at the end of the chars
        while(chars.has(0)) {
            //call lextoken() repedly
            if(chars.peek("/","/")) {
                lexComment();
            }else if(chars.peek("[ \\x08\\n\\r\\t]")){
                //Fix to the specification (No \s)?
                //0x08 is a backspace!
                while(chars.match("[ \\x08\\t\\n\\r]"));
                chars.emit();
           }else
                tokens.add(lexToken());
        }
        return tokens;
    }

    //Fix to the specification
    private void lexComment() {
        chars.match("/");
        chars.match("/");
        while (chars.match("[^\\n\\r]")) ;
        chars.emit();
    }

    private Token lexToken() throws LexException {
            Token tk;
            if(chars.peek("[A-Za-z_][A-Za-z0-9_-]?"))
                tk = lexIdentifier();
            else if(chars.peek( "[0-9]") || chars.peek( "[+\\-]","[0-9]"))
                tk = lexNumber();
            else if(chars.peek("'"))
                tk = lexCharacter();
            else if(chars.peek("\\\""))
                tk = lexString();
            else
                tk =  lexOperator();
            return tk;
    }

    private Token lexIdentifier() throws LexException {
        //[A-Za-z_] [A-Za-z0-9_-]*
        if(chars.match("[A-Za-z_]")) {
            while (chars.match("[A-Za-z0-9_-]")) ;
            return new Token(Token.Type.IDENTIFIER, chars.emit());
        }else
            throw new LexException("Lex Error: Undetermined Identifier");
    }

    private Token lexNumber() throws LexException{
        // [+\-]? [0-9]+ ('.' [0-9]+)? ('e' [0-9]+)  : Number
        boolean decimal = false;
        chars.match("[+\\-]");
        if(chars.peek("[0-9]")) {
            while (chars.match("[0-9]")) ;

            if (chars.peek("\\.", "[0-9]")) {
                decimal = true;
                chars.match("\\.");
                while (chars.match("[0-9]")) ;
            }

            if (chars.peek("e", "[0-9]") ||chars.peek( "e","[+\\-]","[0-9]")) {
                chars.match("e");
                chars.match("[+\\-]");
                while (chars.match("[0-9]")) ;
            }

            if (decimal)
                return new Token(Token.Type.DECIMAL, chars.emit());
            else
                return new Token(Token.Type.INTEGER, chars.emit());
        }else
            throw new LexException("Lex Error: Undetermined Number");
    }

    private Token lexCharacter() throws LexException{
        // ['] ([^'\n\r\\] | escape) [']
        if(chars.match("[']")) {
            if (chars.match("[^'\\n\\r\\\\]")) {
                if (chars.match("[']")) {
                    return new Token(Token.Type.CHARACTER, chars.emit());
                }
            } else if (chars.peek("\\\\")){
                lexEscape();
                if (chars.match("[']"))
                    return new Token(Token.Type.CHARACTER, chars.emit());
            }
        }
        throw new LexException("Lex Token Error: Undetermined CHARACTER");
    }

    private Token lexString() throws LexException{
        //'"' ([^"\n\r\\] | escape)* '"'
        if(!chars.match("[\\\"]"))
            throw new LexException("Not a String");

        //ERROR HERE
        lexEscape();
        while(chars.match("[^\\\"'\\n\\r\\\\]")) {
            lexEscape();
        }
        if(!chars.match("[\\\"]"))
            throw new LexException("Lex Token Error: Undetermined STRING");
        return new Token(Token.Type.STRING, chars.emit());
    }

    private void lexEscape() throws LexException {
        //'\' [bnrt'"\]
        while(chars.peek("\\\\", "[bnrt'\\\"\\\\]")) {
            chars.match("\\\\");
            chars.match("[bnrt'\\\"\\\\]");
        }
    }

    public Token lexOperator() throws LexException {
         //[<>!=] '='? | 'any other character'
        if(chars.peek("[<>!=]")) {
            chars.match("[<>!=]");
            chars.match("[=]");
            return new Token(Token.Type.OPERATOR, chars.emit());
        }else {
            //TODO: Fix bad case
            chars.match("[^A-Za-z_0-9'\" \\x08\\n\\r\\t]");
            return new Token(Token.Type.OPERATOR, chars.emit());
        }
    }

    /**
     * A helper class for maintaining the state of the character stream (input)
     * and methods for building up token literals.
     */
    private static final class CharStream {

        private final String input;
        private int index = 0;
        private int length = 0;

        public CharStream(String input) {
            this.input = input;
        }
        /**
         * Return true if the next character plus an offset
         * (Current Caharcter if we are looking ata a token)
         * is less than the length
        * */
        public boolean has(int offset) {
            return index + offset < input.length();
        }


        /**
         * Returns true if the next characters match their corresponding
         * pattern. Each pattern is a regex matching only ONE character!
         *
         * Helper Function for match
         */
        public boolean peek(String... patterns) {
            if (!has(patterns.length - 1)) {
                return false;
            }
            for (int offset = 0; offset < patterns.length; offset++) {
                var character = input.charAt(index + offset);
                if (!String.valueOf(character).matches(patterns[offset])) {
                    return false;
                }
            }
            return true;
        }

        /**
         * Equivalent to peek, but also advances the character stream.
         */
        public boolean match(String... patterns) {
            var peek = peek(patterns);
            if (peek) {
                index += patterns.length;
                length += patterns.length;
            }
            return peek;
        }

        /**
         * Returns the literal built by all characters matched since the last
         * call to emit(); also resetting the length for subsequent tokens.
         *
         * For Returning a token
         */
        public String emit() {
            var literal = input.substring(index - length, index);
            length = 0;
            return literal;
        }

    }

}
