package adris.altoclef.tasks.construction.build_structure;

import java.util.*;
import java.util.function.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import adris.altoclef.AltoClefController;

/**
 * MiniBlocks: a tiny interpreter that parses and runs a small language with
 * - let / assignment
 * - if / else
 * - while
 * - for (init; condition; update) { ... }
 * - setBlock(x, y, z, facing, blockname)
 *
 * As it executes, each setBlock call becomes a SetBlockCommand. Use
 * Runner.next()
 * to pull commands one-by-one until completion.
 */
public class StructureFromCode {
    public static final Logger LOGGER = LogManager.getLogger();

    // ==== Public API ====

    /** Represents a single emitted setBlock command. */
    public static final class SetBlockCommand {
        public final int x, y, z;
        public final String blockName;

        public SetBlockCommand(int x, int y, int z, String blockName) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockName = blockName;
        }

        @Override
        public String toString() {
            return "setBlock(" + x + ", " + y + ", " + z + ", \"" + blockName + "\")";
        }
    }

    /** Compiles source into an executable Program. */
    public static Program compile(String source) {
        Lexer lex = new Lexer(source);
        List<Token> tokens = lex.lex();
        Parser parser = new Parser(tokens);
        List<Stmt> stmts = parser.parse();
        return new Program(stmts);
    }

    /** Program runner that yields setBlock commands lazily, step-by-step. */
    public static final class Runner {
        private final Interpreter interp;
        private final Queue<SetBlockCommand> queue = new ArrayDeque<>();
        private boolean done = false;

        public Runner(Program program) {
            this.interp = new Interpreter(program, queue::add);
        }

        /** Execute until the next setBlock is produced or program ends. */
        public Optional<SetBlockCommand> next() {
            if (done && queue.isEmpty())
                return Optional.empty();
            if (!queue.isEmpty())
                return Optional.of(queue.poll());
            while (queue.isEmpty() && !done) {
                done = !interp.step(); // step returns false when finished
            }
            return queue.isEmpty() ? Optional.empty() : Optional.of(queue.poll());
        }
    }

    /** Container for parsed program (AST). */
    public static final class Program {
        final List<Stmt> statements;

        Program(List<Stmt> statements) {
            this.statements = statements;
        }
    }

    public static void runCode(String code, Consumer<SetBlockCommand> onSetBlock, AltoClefController mod)
            throws Exception {
        Program program = compile(code);
        Runner runner = new Runner(program);
        Optional<SetBlockCommand> cmd;
        while ((cmd = runner.next()).isPresent()) {
            if (mod.isStopping) {
                // mod.isStopping = false; // maybe don't need?
                return;
            }
            SetBlockCommand data = cmd.get();
            onSetBlock.accept(data);
        }
    }

    // ==== Lexer ====

    enum TokenType {
        // Single-char
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACE, RIGHT_BRACE, COMMA, DOT, MINUS, PLUS, PERCENT, SEMICOLON, SLASH, STAR,
        // One or two char
        BANG, BANG_EQUAL, EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,

        // Booleans and ternary
        AND_AND, OR_OR, QUESTION, COLON,

        // Literals
        IDENTIFIER, STRING, NUMBER,
        // Keywords
        LET, IF, ELSE, WHILE, FOR, TRUE, FALSE, NIL, SETBLOCK,
        // End
        EOF
    }

    static final class Token {
        final TokenType type;
        final String lexeme;
        final Object literal;
        final int line, col;

        Token(TokenType type, String lexeme, Object literal, int line, int col) {
            this.type = type;
            this.lexeme = lexeme;
            this.literal = literal;
            this.line = line;
            this.col = col;
        }

        @Override
        public String toString() {
            return type + " '" + lexeme + "'" + (literal != null ? (" -> " + literal) : "");
        }
    }

    static final class Lexer {
        private final String src;
        private final List<Token> tokens = new ArrayList<>();
        private int start = 0, current = 0, line = 1, col = 1;

        Lexer(String src) {
            this.src = src;
        }

        List<Token> lex() {
            while (!isAtEnd()) {
                start = current;
                scanToken();
            }
            tokens.add(new Token(TokenType.EOF, "", null, line, col));
            return tokens;
        }

        private void scanToken() {
            char c = advance();
            switch (c) {
                case '(':
                    add(TokenType.LEFT_PAREN);
                    break;
                case ')':
                    add(TokenType.RIGHT_PAREN);
                    break;
                case '{':
                    add(TokenType.LEFT_BRACE);
                    break;
                case '}':
                    add(TokenType.RIGHT_BRACE);
                    break;
                case ',':
                    add(TokenType.COMMA);
                    break;
                case '.':
                    add(TokenType.DOT);
                    break;
                case '-':
                    add(TokenType.MINUS);
                    break;
                case '+':
                    add(TokenType.PLUS);
                    break;
                case ';':
                    add(TokenType.SEMICOLON);
                    break;
                case '*':
                    add(TokenType.STAR);
                    break;
                case '%':
                    add(TokenType.PERCENT);
                    break;
                case '!':
                    add(match('=') ? TokenType.BANG_EQUAL : TokenType.BANG);
                    break;
                case '=':
                    add(match('=') ? TokenType.EQUAL_EQUAL : TokenType.EQUAL);
                    break;
                case '<':
                    add(match('=') ? TokenType.LESS_EQUAL : TokenType.LESS);
                    break;
                case '>':
                    add(match('=') ? TokenType.GREATER_EQUAL : TokenType.GREATER);
                    break;

                case ' ':
                case '\r':
                case '\t':
                    break;
                case '\n':
                    line++;
                    col = 0;
                    break;
                case '"':
                    string();
                    break;
                case '&':
                    if (match('&'))
                        add(TokenType.AND_AND);
                    else
                        error("Unexpected character: & (did you mean &&?)");
                    break;
                case '|':
                    if (match('|'))
                        add(TokenType.OR_OR);
                    else
                        error("Unexpected character: | (did you mean ||?)");
                    break;

                // NEW: ternary
                case '?':
                    add(TokenType.QUESTION);
                    break;
                case ':':
                    add(TokenType.COLON);
                    break;
                case '/':
                    if (match('/')) {
                        while (!isAtEnd() && peek() != '\n')
                            advance();
                    } else if (match('*')) { /* …existing block comment logic… */
                        while (!isAtEnd() && !(peek() == '*' && peekNext() == '/')) {
                            if (peek() == '\n') {
                                line++;
                                col = 0;
                            }
                            advance();
                        }
                        if (!isAtEnd()) {
                            advance();
                            advance();
                        }
                    } else
                        add(TokenType.SLASH);
                    break;
                default:
                    if (isDigit(c))
                        number();
                    else if (isAlpha(c))
                        identifier();
                    else
                        error("Unexpected character: " + c);
            }
        }

        private void string() {
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd() && peek() != '"') {
                char c = advance();
                if (c == '\\') {
                    if (isAtEnd())
                        break;
                    char n = advance();
                    switch (n) {
                        case 'n':
                            sb.append('\n');
                            break;
                        case 't':
                            sb.append('\t');
                            break;
                        case '"':
                            sb.append('"');
                            break;
                        case '\\':
                            sb.append('\\');
                            break;
                        default:
                            sb.append(n);
                            break;
                    }
                } else {
                    sb.append(c);
                }
                if (c == '\n') {
                    line++;
                    col = 0;
                }
            }
            if (isAtEnd())
                error("Unterminated string.");
            advance(); // closing "
            add(TokenType.STRING, sb.toString());
        }

        private void number() {
            while (isDigit(peek()))
                advance();
            if (peek() == '.' && isDigit(peekNext())) {
                advance();
                while (isDigit(peek()))
                    advance();
            }
            double val = Double.parseDouble(src.substring(start, current));
            add(TokenType.NUMBER, val);
        }

        private void identifier() {
            while (isAlphaNumeric(peek()))
                advance();
            String text = src.substring(start, current);
            TokenType type = keywords.get(text);
            if ("setBlock".equals(text))
                type = TokenType.SETBLOCK;
            if (type == null)
                type = TokenType.IDENTIFIER;
            add(type);
        }

        private void add(TokenType type) {
            add(type, null);
        }

        private void add(TokenType type, Object lit) {
            tokens.add(new Token(type, src.substring(start, current), lit, line, col));
        }

        private boolean match(char expected) {
            if (isAtEnd() || src.charAt(current) != expected)
                return false;
            advance();
            return true;
        }

        private char peek() {
            return isAtEnd() ? '\0' : src.charAt(current);
        }

        private char peekNext() {
            return (current + 1 >= src.length()) ? '\0' : src.charAt(current + 1);
        }

        private char advance() {
            current++;
            col++;
            return src.charAt(current - 1);
        }

        private boolean isAtEnd() {
            return current >= src.length();
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }

        private static boolean isAlpha(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
        }

        private static boolean isAlphaNumeric(char c) {
            return isAlpha(c) || isDigit(c);
        }

        private void error(String msg) {
            throw new RuntimeException("[Lexer] line " + line + ", col " + col + ": " + msg);
        }

        private static final Map<String, TokenType> keywords = new HashMap<>();
        static {
            keywords.put("let", TokenType.LET);
            keywords.put("if", TokenType.IF);
            keywords.put("else", TokenType.ELSE);
            keywords.put("while", TokenType.WHILE);
            keywords.put("for", TokenType.FOR);
            keywords.put("true", TokenType.TRUE);
            keywords.put("false", TokenType.FALSE);
            keywords.put("nil", TokenType.NIL);
        }
    }

    // ==== AST ====

    interface Expr {
        <R> R accept(ExprVisitor<R> v);
    }

    interface ExprVisitor<R> {
        R visitBinary(Binary e);

        R visitUnary(Unary e);

        R visitLiteral(Literal e);

        R visitGrouping(Grouping e);

        R visitVariable(Variable e);

        R visitAssign(Assign e);

        R visitConditional(Conditional e);
    }

    static final class Conditional implements Expr {
        final Expr condition;
        final Expr thenExpr;
        final Expr elseExpr;

        Conditional(Expr condition, Expr thenExpr, Expr elseExpr) {
            this.condition = condition;
            this.thenExpr = thenExpr;
            this.elseExpr = elseExpr;
        }

        public <R> R accept(ExprVisitor<R> v) {
            return v.visitConditional(this);
        }
    }

    static final class Binary implements Expr {
        final Expr left;
        final Token op;
        final Expr right;

        Binary(Expr l, Token o, Expr r) {
            left = l;
            op = o;
            right = r;
        }

        public <R> R accept(ExprVisitor<R> v) {
            return v.visitBinary(this);
        }
    }

    static final class Unary implements Expr {
        final Token op;
        final Expr right;

        Unary(Token o, Expr r) {
            op = o;
            right = r;
        }

        public <R> R accept(ExprVisitor<R> v) {
            return v.visitUnary(this);
        }
    }

    static final class Literal implements Expr {
        final Object value;

        Literal(Object v) {
            value = v;
        }

        public <R> R accept(ExprVisitor<R> v) {
            return v.visitLiteral(this);
        }
    }

    static final class Grouping implements Expr {
        final Expr expr;

        Grouping(Expr e) {
            expr = e;
        }

        public <R> R accept(ExprVisitor<R> v) {
            return v.visitGrouping(this);
        }
    }

    static final class Variable implements Expr {
        final Token name;

        Variable(Token n) {
            name = n;
        }

        public <R> R accept(ExprVisitor<R> v) {
            return v.visitVariable(this);
        }
    }

    static final class Assign implements Expr {
        final Token name;
        final Expr value;

        Assign(Token n, Expr v) {
            name = n;
            value = v;
        }

        public <R> R accept(ExprVisitor<R> v) {
            return v.visitAssign(this);
        }
    }

    interface Stmt {
        void accept(StmtVisitor v);
    }

    interface StmtVisitor {
        void visitExprStmt(ExprStmt s);

        void visitPrintStmt(PrintStmt s); // (not exposed, handy for debugging)

        void visitVarStmt(Var s);

        void visitBlockStmt(Block s);

        void visitIfStmt(If s);

        void visitWhileStmt(While s);

        void visitForStmt(For s);

        void visitSetBlockStmt(SetBlock s);
    }

    static final class ExprStmt implements Stmt {
        final Expr expr;

        ExprStmt(Expr e) {
            expr = e;
        }

        public void accept(StmtVisitor v) {
            v.visitExprStmt(this);
        }
    }

    static final class PrintStmt implements Stmt {
        final Expr expr;

        PrintStmt(Expr e) {
            expr = e;
        }

        public void accept(StmtVisitor v) {
            v.visitPrintStmt(this);
        }
    }

    static final class Var implements Stmt {
        final Token name;
        final Expr initializer;

        Var(Token n, Expr init) {
            name = n;
            initializer = init;
        }

        public void accept(StmtVisitor v) {
            v.visitVarStmt(this);
        }
    }

    static final class Block implements Stmt {
        final List<Stmt> statements;

        Block(List<Stmt> s) {
            statements = s;
        }

        public void accept(StmtVisitor v) {
            v.visitBlockStmt(this);
        }
    }

    static final class If implements Stmt {
        final Expr condition;
        final Stmt thenBranch;
        final Stmt elseBranch;

        If(Expr c, Stmt t, Stmt e) {
            condition = c;
            thenBranch = t;
            elseBranch = e;
        }

        public void accept(StmtVisitor v) {
            v.visitIfStmt(this);
        }
    }

    static final class While implements Stmt {
        final Expr condition;
        final Stmt body;

        While(Expr c, Stmt b) {
            condition = c;
            body = b;
        }

        public void accept(StmtVisitor v) {
            v.visitWhileStmt(this);
        }
    }

    static final class For implements Stmt {
        final Stmt initializer;
        final Expr condition;
        final Stmt increment;
        final Stmt body;

        For(Stmt init, Expr cond, Stmt inc, Stmt body) {
            initializer = init;
            condition = cond;
            increment = inc;
            this.body = body;
        }

        public void accept(StmtVisitor v) {
            v.visitForStmt(this);
        }
    }

    static final class SetBlock implements Stmt {
        final Expr x, y, z, block;

        SetBlock(Expr x, Expr y, Expr z, Expr block) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
        }

        public void accept(StmtVisitor v) {
            v.visitSetBlockStmt(this);
        }
    }

    // ==== Parser ====

    static final class Parser {
        private final List<Token> tokens;
        private int current = 0;

        Parser(List<Token> tokens) {
            this.tokens = tokens;
        }

        List<Stmt> parse() {
            List<Stmt> stmts = new ArrayList<>();
            while (!isAtEnd())
                stmts.add(declaration());
            return stmts;
        }

        private Stmt declaration() {
            if (match(TokenType.LET))
                return varDeclaration();
            return statement();
        }

        private Stmt varDeclaration() {
            Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");
            Expr init = null;
            if (match(TokenType.EQUAL))
                init = expression();
            consume(TokenType.SEMICOLON, "Expect ';' after variable declaration.");
            return new Var(name, init == null ? new Literal(null) : init);
        }

        private Stmt statement() {
            if (match(TokenType.LEFT_BRACE))
                return new Block(block());
            if (match(TokenType.IF))
                return ifStatement();
            if (match(TokenType.WHILE))
                return whileStatement();
            if (match(TokenType.FOR))
                return forStatement();
            if (match(TokenType.SETBLOCK))
                return setBlockStatement();
            return exprStatement();
        }

        private Stmt setBlockStatement() {
            consume(TokenType.LEFT_PAREN, "Expect '(' after setBlock.");
            Expr x = expression();
            consume(TokenType.COMMA, "Expect ',' after x.");
            Expr y = expression();
            consume(TokenType.COMMA, "Expect ',' after y.");
            Expr z = expression();
            consume(TokenType.COMMA, "Expect ',' after z.");
            Expr block = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after arguments.");
            consume(TokenType.SEMICOLON, "Expect ';' after setBlock.");
            return new SetBlock(x, y, z, block);
        }

        private Stmt ifStatement() {
            consume(TokenType.LEFT_PAREN, "Expect '(' after if.");
            Expr cond = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
            Stmt thenB = statement();
            Stmt elseB = null;
            if (match(TokenType.ELSE))
                elseB = statement();
            return new If(cond, thenB, elseB);
        }

        private Stmt whileStatement() {
            consume(TokenType.LEFT_PAREN, "Expect '(' after while.");
            Expr cond = expression();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after condition.");
            Stmt body = statement();
            return new While(cond, body);
        }

        private Stmt forStatement() {
            consume(TokenType.LEFT_PAREN, "Expect '(' after for.");

            Stmt init;
            if (match(TokenType.SEMICOLON)) {
                init = null;
            } else if (match(TokenType.LET)) {
                init = varDeclarationNoSemi();
                consume(TokenType.SEMICOLON, "Expect ';' after for init.");
            } else {
                init = exprStatementNoSemi();
                consume(TokenType.SEMICOLON, "Expect ';' after for init.");
            }

            Expr cond = null;
            if (!check(TokenType.SEMICOLON))
                cond = expression();
            consume(TokenType.SEMICOLON, "Expect ';' after loop condition.");

            Stmt inc = null;
            if (!check(TokenType.RIGHT_PAREN))
                inc = exprStatementNoSemi();
            consume(TokenType.RIGHT_PAREN, "Expect ')' after for clauses.");

            Stmt body = statement();

            // Desugar to: { init; while (cond) { body; inc; } }
            if (inc != null)
                body = new Block(Arrays.asList(body, inc));
            if (cond == null)
                cond = new Literal(true);
            Stmt whileStmt = new While(cond, body);
            if (init != null)
                whileStmt = new Block(Arrays.asList(init, whileStmt));
            return whileStmt;
        }

        private Stmt exprStatement() {
            Expr e = expression();
            consume(TokenType.SEMICOLON, "Expect ';' after expression.");
            return new ExprStmt(e);
        }

        private Stmt exprStatementNoSemi() {
            Expr e = expression();
            return new ExprStmt(e);
        }

        private Stmt varDeclarationNoSemi() {
            Token name = consume(TokenType.IDENTIFIER, "Expect variable name.");
            Expr init = null;
            if (match(TokenType.EQUAL))
                init = expression();
            return new Var(name, init == null ? new Literal(null) : init);
        }

        private List<Stmt> block() {
            List<Stmt> stmts = new ArrayList<>();
            while (!check(TokenType.RIGHT_BRACE) && !isAtEnd())
                stmts.add(declaration());
            consume(TokenType.RIGHT_BRACE, "Expect '}' after block.");
            return stmts;
        }

        // Expressions (precedence: equality > comparison > term > factor > unary >
        // primary)
        private Expr expression() {
            return assignment();
        }

        private Expr assignment() {
            Expr expr = conditional(); // CHANGED: used to be equality()
            if (match(TokenType.EQUAL)) {
                Token equals = previous();
                Expr value = assignment();
                if (expr instanceof Variable) {
                    Token name = ((Variable) expr).name;
                    return new Assign(name, value);
                }
                error(equals, "Invalid assignment target.");
            }
            return expr;
        }

        // NEW: ternary (right-associative)
        private Expr conditional() {
            Expr expr = or();
            if (match(TokenType.QUESTION)) {
                Expr thenExpr = expression(); // allow comma/ops etc.
                consume(TokenType.COLON, "Expect ':' in ternary expression.");
                Expr elseExpr = conditional(); // right-associative
                expr = new Conditional(expr, thenExpr, elseExpr);
            }
            return expr;
        }

        // NEW: || precedence
        private Expr or() {
            Expr expr = and();
            while (match(TokenType.OR_OR)) {
                Token op = previous();
                Expr right = and();
                expr = new Binary(expr, op, right);
            }
            return expr;
        }

        // NEW: && precedence
        private Expr and() {
            Expr expr = equality();
            while (match(TokenType.AND_AND)) {
                Token op = previous();
                Expr right = equality();
                expr = new Binary(expr, op, right);
            }
            return expr;
        }

        private Expr equality() {
            Expr expr = comparison();
            while (match(TokenType.BANG_EQUAL, TokenType.EQUAL_EQUAL)) {
                Token op = previous();
                Expr right = comparison();
                expr = new Binary(expr, op, right);
            }
            return expr;
        }

        private Expr comparison() {
            Expr expr = term();
            while (match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
                Token op = previous();
                Expr right = term();
                expr = new Binary(expr, op, right);
            }
            return expr;
        }

        private Expr term() {
            Expr expr = factor();
            while (match(TokenType.PLUS, TokenType.MINUS)) {
                Token op = previous();
                Expr right = factor();
                expr = new Binary(expr, op, right);
            }
            return expr;
        }

        private Expr factor() {
            Expr expr = unary();
            while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
                Token op = previous();
                Expr right = unary();
                expr = new Binary(expr, op, right);
            }
            return expr;
        }

        private Expr unary() {
            if (match(TokenType.BANG, TokenType.MINUS)) {
                Token op = previous();
                Expr right = unary();
                return new Unary(op, right);
            }
            return primary();
        }

        private Expr primary() {
            if (match(TokenType.FALSE))
                return new Literal(false);
            if (match(TokenType.TRUE))
                return new Literal(true);
            if (match(TokenType.NIL))
                return new Literal(null);
            if (match(TokenType.NUMBER))
                return new Literal(previous().literal);
            if (match(TokenType.STRING))
                return new Literal(previous().literal);
            if (match(TokenType.IDENTIFIER))
                return new Variable(previous());
            if (match(TokenType.LEFT_PAREN)) {
                Expr e = expression();
                consume(TokenType.RIGHT_PAREN, "Expect ')' after expression.");
                return new Grouping(e);
            }
            error(peek(), "Expect expression.");
            return null; // unreachable
        }

        // Helpers
        private boolean match(TokenType... types) {
            for (TokenType t : types) {
                if (check(t)) {
                    advance();
                    return true;
                }
            }
            return false;
        }

        private boolean check(TokenType t) {
            return !isAtEnd() && peek().type == t;
        }

        private Token advance() {
            if (!isAtEnd())
                current++;
            return previous();
        }

        private boolean isAtEnd() {
            return peek().type == TokenType.EOF;
        }

        private Token peek() {
            return tokens.get(current);
        }

        private Token previous() {
            return tokens.get(current - 1);
        }

        private Token consume(TokenType t, String msg) {
            if (check(t))
                return advance();
            error(peek(), msg);
            return null; // unreachable
        }

        private void error(Token t, String msg) {
            throw new RuntimeException(
                    "[Parser] line " + t.line + ", col " + t.col + ": " + msg + " Found '" + t.lexeme + "'");
        }
    }

    // ==== Interpreter with step support ====

    static final class Interpreter implements ExprVisitor<Object>, StmtVisitor {
        private final Program program;
        private final Emitter emitter;
        private final Deque<Env> envStack = new ArrayDeque<>();
        private final Deque<Frame> frames = new ArrayDeque<>();
        private boolean initialized = false;

        interface Emitter {
            void emit(SetBlockCommand cmd);
        }

        Interpreter(Program program, Emitter emitter) {
            this.program = program;
            this.emitter = emitter;
            envStack.push(new Env(null));
        }

        /**
         * Executes until either a setBlock is emitted, or the whole program finishes.
         * 
         * @return true if there is more to run; false if finished.
         */
        boolean step() {
            if (!initialized) {
                frames.push(new Frame(program.statements));
                initialized = true;
            }
            while (!frames.isEmpty()) {
                Frame f = frames.peek();

                // Handle WhileFrame specially
                if (f instanceof WhileFrame) {
                    WhileFrame wf = (WhileFrame) f;
                    if (isTruthy(evaluate(wf.condition))) {
                        // Run one iteration body, then come back to this WhileFrame
                        frames.push(new Frame(singleton(wf.body)));
                        continue;
                    } else {
                        frames.pop(); // loop finished
                        continue;
                    }
                }

                if (f.ip >= f.stmts.size()) {
                    frames.pop();
                    if (f.onClose != null)
                        f.onClose.run();
                    continue;
                }

                Stmt s = f.stmts.get(f.ip++);
                int emittedBefore = emittedCount;
                s.accept(this);
                if (emittedCount > emittedBefore)
                    return true; // yielded one setBlock
            }
            return false; // finished
        }

        // Track emissions to know when to yield
        private int emittedCount = 0;

        private void emit(SetBlockCommand cmd) {
            emittedCount++;
            emitter.emit(cmd);
        }

        // ---- Statements ----

        public void visitExprStmt(ExprStmt s) {
            evaluate(s.expr);
        }

        public void visitPrintStmt(PrintStmt s) {
            System.out.println(stringify(evaluate(s.expr)));
        }

        public void visitVarStmt(Var s) {
            Object val = evaluate(s.initializer);
            env().define(s.name.lexeme, val);
        }

        public void visitBlockStmt(Block s) {
            pushEnv();
            frames.push(new Frame(s.statements, () -> popEnv()));
        }

        public void visitIfStmt(If s) {
            if (isTruthy(evaluate(s.condition))) {
                frames.push(new Frame(singleton(s.thenBranch)));
            } else if (s.elseBranch != null) {
                frames.push(new Frame(singleton(s.elseBranch)));
            }
        }

        public void visitWhileStmt(While s) {
            frames.push(new WhileFrame(s.condition, s.body));
        }

        public void visitForStmt(For s) {
            // Parser desugars 'for' into a block+while, so this isn't used.
            frames.push(new Frame(singleton(s.body)));
        }

        public void visitSetBlockStmt(SetBlock s) {
            int x = toInt(evaluate(s.x));
            int y = toInt(evaluate(s.y));
            int z = toInt(evaluate(s.z));
            String block = String.valueOf(evaluate(s.block));
            emit(new SetBlockCommand(x, y, z, block));
        }

        // ---- Expressions ----
        public Object visitBinary(Binary e) {
            // Short-circuit for logical ops:
            if (e.op.type == TokenType.OR_OR) {
                Object l = evaluate(e.left);
                if (isTruthy(l))
                    return true; // short-circuit
                return isTruthy(evaluate(e.right));
            }
            if (e.op.type == TokenType.AND_AND) {
                Object l = evaluate(e.left);
                if (!isTruthy(l))
                    return false; // short-circuit
                return isTruthy(evaluate(e.right));
            }

            Object l = evaluate(e.left);
            Object r = evaluate(e.right);
            switch (e.op.type) {
                case PLUS:
                    if (l instanceof Double && r instanceof Double)
                        return (Double) l + (Double) r;
                    return stringify(l) + stringify(r);
                case MINUS:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (Double) l - (Double) r;
                case STAR:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (Double) l * (Double) r;
                case SLASH:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (Double) l / (Double) r;
                case PERCENT:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (double) l % (double) r;
                case GREATER:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (Double) l > (Double) r;
                case GREATER_EQUAL:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (Double) l >= (Double) r;
                case LESS:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (Double) l < (Double) r;
                case LESS_EQUAL:
                    checkNumber(l, e.op);
                    checkNumber(r, e.op);
                    return (Double) l <= (Double) r;
                case EQUAL_EQUAL:
                    return isEqual(l, r);
                case BANG_EQUAL:
                    return !isEqual(l, r);
                default:
                    throw new RuntimeException("Unknown binary op: " + e.op.type);
            }
        }

        public Object visitConditional(Conditional e) {
            Object cond = evaluate(e.condition);
            if (isTruthy(cond))
                return evaluate(e.thenExpr);
            return evaluate(e.elseExpr);
        }

        public Object visitUnary(Unary e) {
            Object v = evaluate(e.right);
            switch (e.op.type) {
                case MINUS:
                    checkNumber(v, e.op);
                    return -((Double) v);
                case BANG:
                    return !isTruthy(v);
                default:
                    throw new RuntimeException("Unknown unary op: " + e.op.type);
            }
        }

        public Object visitLiteral(Literal e) {
            return e.value;
        }

        public Object visitGrouping(Grouping e) {
            return evaluate(e.expr);
        }

        public Object visitVariable(Variable e) {
            return env().get(e.name);
        }

        public Object visitAssign(Assign e) {
            Object val = evaluate(e.value);
            env().assign(e.name, val);
            return val;
        }

        private Object evaluate(Expr e) {
            return e.accept(this);
        }

        // ---- Utilities ----

        private Env env() {
            return envStack.peek();
        }

        private void pushEnv() {
            envStack.push(new Env(env()));
        }

        private void popEnv() {
            envStack.pop();
        }

        private static boolean isTruthy(Object v) {
            if (v == null)
                return false;
            if (v instanceof Boolean)
                return (Boolean) v;
            if (v instanceof Double)
                return ((Double) v) != 0.0;
            String s = String.valueOf(v);
            return !s.isEmpty();
        }

        private static boolean isEqual(Object a, Object b) {
            if (a == null && b == null)
                return true;
            if (a == null)
                return false;
            if (a instanceof Double && b instanceof Double)
                return ((Double) a).doubleValue() == ((Double) b).doubleValue();
            return String.valueOf(a).equals(String.valueOf(b));
        }

        private static void checkNumber(Object v, Token at) {
            if (!(v instanceof Double))
                throw new RuntimeException("Operand must be a number at token '" + at.lexeme + "'");
        }

        private static String stringify(Object v) {
            if (v == null)
                return "nil";
            if (v instanceof Double) {
                double d = (Double) v;
                if (d == Math.rint(d))
                    return String.valueOf((long) d);
                return String.valueOf(d);
            }
            return String.valueOf(v);
        }

        private static int toInt(Object v) {
            if (v instanceof Double)
                return (int) Math.round((Double) v);
            try {
                return Integer.parseInt(String.valueOf(v));
            } catch (Exception e) {
                throw new RuntimeException("Expected integer-like value, got: " + v);
            }
        }

        // ---- Frames for stepping ----

        static class Frame {
            final List<Stmt> stmts;
            int ip = 0;
            final Runnable onClose; // optional cleanup (e.g., pop env)

            Frame(List<Stmt> stmts) {
                this(stmts, null);
            }

            Frame(List<Stmt> stmts, Runnable onClose) {
                this.stmts = stmts;
                this.onClose = onClose;
            }
        }

        static final class WhileFrame extends Frame {
            final Expr condition;
            final Stmt body;

            WhileFrame(Expr condition, Stmt body) {
                super(Collections.emptyList());
                this.condition = condition;
                this.body = body;
            }
        }

        private static List<Stmt> singleton(Stmt s) {
            return Collections.singletonList(s);
        }
    }

    // ==== Environment / Variables ====

    static final class Env {
        private final Env enclosing;
        private final Map<String, Object> values = new HashMap<>();

        Env(Env enclosing) {
            this.enclosing = enclosing;
        }

        void define(String name, Object value) {
            values.put(name, value);
        }

        Object get(Token nameTok) {
            String name = nameTok.lexeme;
            if (values.containsKey(name))
                return values.get(name);
            if (enclosing != null)
                return enclosing.get(nameTok);
            throw new RuntimeException("Undefined variable '" + name + "'.");
        }

        void assign(Token nameTok, Object value) {
            String name = nameTok.lexeme;
            if (values.containsKey(name)) {
                values.put(name, value);
                return;
            }
            if (enclosing != null) {
                enclosing.assign(nameTok, value);
                return;
            }
            throw new RuntimeException("Undefined variable '" + name + "'.");
        }
    }

    // static String code = String.join("\n",
    // "let baseX = 10;",
    // "let baseY = 64;",
    // "let baseZ = 10;",
    // "let dir = \"north\";",
    // "let block = \"stone\";",
    // "let t = true;",
    // "",
    // "// Build a 3x2 wall:",
    // "for (let i = 0; i < 3; i = i + 1) {",
    // " for (let j = 0; j < 2; j = j + 1) {",
    // " setBlock(baseX + i, baseY + j, baseZ, (!t || j != 0)? block : \"a\");",
    // " }",
    // "}",
    // "",
    // "// If we want a cap:",
    // "if (true) {",
    // " setBlock(baseX + 1, baseY + 2, baseZ, \"glass\");",
    // "}");

    // public static void main(String[] args) {
    // try {
    // runCode(code, cmd -> System.out.println(cmd));
    // } catch (Exception e) {
    // // TODO: handle exception
    // }
    // }

    public static void buildStructureFromCode(String code, Consumer<SetBlockCommand> onSetBlock,
            Consumer<String> onErrString, Runnable onFinishSuccess, AltoClefController mod) {
        try {
            // run once to validate
            StructureFromCode.runCode(code,
                    (_unused) -> {
                        // set block is nothing
                    }, mod);
            LOGGER.info("Code validated, running code for real now.");
            // code validated, can safely set block
            StructureFromCode.runCode(code, onSetBlock, mod);
            onFinishSuccess.run();
        } catch (Exception e) {
            String err = e.getMessage();
            String error = err == null ? "unknown error" : err;
            LOGGER.error("LLM build structure err={} ", error);
            onErrString.accept(error);
        }
    }

}