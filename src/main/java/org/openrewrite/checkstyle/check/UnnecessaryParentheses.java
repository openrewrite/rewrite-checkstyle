/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.checkstyle.check;

import org.eclipse.microprofile.config.Config;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.Cursor;
import org.openrewrite.checkstyle.policy.ParenthesesToken;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Set;

import static org.openrewrite.checkstyle.policy.ParenthesesToken.*;

public class UnnecessaryParentheses extends CheckstyleRefactorVisitor {
    private static final Set<ParenthesesToken> DEFAULT_TOKENS = Set.of(
            EXPR,
            IDENT,
            NUM_DOUBLE,
            NUM_FLOAT,
            NUM_INT,
            NUM_LONG,
            STRING_LITERAL,
            LITERAL_NULL,
            LITERAL_FALSE,
            LITERAL_TRUE,
            ASSIGN,
            BAND_ASSIGN,
            BOR_ASSIGN,
            BSR_ASSIGN,
            BXOR_ASSIGN,
            DIV_ASSIGN,
            MINUS_ASSIGN,
            MOD_ASSIGN,
            PLUS_ASSIGN,
            SL_ASSIGN,
            SR_ASSIGN,
            STAR_ASSIGN,
            LAMBDA
    );

    private final Set<ParenthesesToken> tokens;

    public UnnecessaryParentheses(Set<ParenthesesToken> tokens) {
        super("checkstyle.UnnecessaryParentheses");
        this.tokens = tokens;
        setCursoringOn();
    }

    @AutoConfigure
    public static UnnecessaryParentheses configure(Config config) {
        return fromModule(
                config,
                "UnnecessaryParentheses",
                m -> new UnnecessaryParentheses(m.propAsTokens(ParenthesesToken.class, DEFAULT_TOKENS))
        );
    }

    @Override
    public <T extends J> J visitParentheses(J.Parentheses<T> parens) {
        if (parens.getTree() instanceof J.Ident && tokens.contains(IDENT)) {
            maybeUnwrapParentheses(getCursor());
        }

        return super.visitParentheses(parens);
    }

    @Override
    public J visitLiteral(J.Literal literal) {
        JavaType.Primitive type = literal.getType();

        if ((tokens.contains(NUM_INT) && type == JavaType.Primitive.Int) ||
                (tokens.contains(NUM_DOUBLE) && type == JavaType.Primitive.Double) ||
                (tokens.contains(NUM_LONG) && type == JavaType.Primitive.Long) ||
                (tokens.contains(NUM_FLOAT) && type == JavaType.Primitive.Float) ||
                (tokens.contains(STRING_LITERAL) && type == JavaType.Primitive.String) ||
                (tokens.contains(LITERAL_FALSE) && type == JavaType.Primitive.Boolean && literal.getValue() == Boolean.valueOf(false)) ||
                (tokens.contains(LITERAL_TRUE) && type == JavaType.Primitive.Boolean && literal.getValue() == Boolean.valueOf(true))) {
            maybeUnwrapParentheses(getCursor());
        }

        return super.visitLiteral(literal);
    }

    @Override
    public J visitAssignOp(J.AssignOp assignOp) {
        Expression assignment = assignOp.getAssignment();
        J.AssignOp.Operator op = assignOp.getOperator();
        if (assignment instanceof J.Parentheses && ((tokens.contains(BAND_ASSIGN) && op instanceof J.AssignOp.Operator.BitAnd) ||
                (tokens.contains(BOR_ASSIGN) && op instanceof J.AssignOp.Operator.BitOr) ||
                (tokens.contains(BSR_ASSIGN) && op instanceof J.AssignOp.Operator.UnsignedRightShift) ||
                (tokens.contains(BXOR_ASSIGN) && op instanceof J.AssignOp.Operator.BitXor) ||
                (tokens.contains(SR_ASSIGN) && op instanceof J.AssignOp.Operator.RightShift) ||
                (tokens.contains(SL_ASSIGN) && op instanceof J.AssignOp.Operator.LeftShift) ||
                (tokens.contains(MINUS_ASSIGN) && op instanceof J.AssignOp.Operator.Subtraction) ||
                (tokens.contains(DIV_ASSIGN) && op instanceof J.AssignOp.Operator.Division) ||
                (tokens.contains(PLUS_ASSIGN) && op instanceof J.AssignOp.Operator.Addition) ||
                (tokens.contains(STAR_ASSIGN) && op instanceof J.AssignOp.Operator.Multiplication) ||
                (tokens.contains(MOD_ASSIGN) && op instanceof J.AssignOp.Operator.Modulo))) {

            maybeUnwrapParentheses(new Cursor(getCursor(), assignment));
        }

        return super.visitAssignOp(assignOp);
    }

    @Override
    public J visitAssign(J.Assign assign) {
        if (tokens.contains(ASSIGN)) {
            maybeUnwrapParentheses(getCursor());
        }
        return super.visitAssign(assign);
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        if (tokens.contains(ASSIGN) && variable.getInitializer() != null) {
            maybeUnwrapParentheses(new Cursor(getCursor(), variable.getInitializer()));
        }

        return super.visitVariable(variable);
    }

    @Override
    public J visitLambda(J.Lambda lambda) {
        J.Lambda l = refactor(lambda, super::visitLambda);
        if (lambda.getParamSet().getParams().size() == 1 &&
                lambda.getParamSet().isParenthesized() &&
                lambda.getParamSet().getParams().get(0) instanceof J.VariableDecls &&
                ((J.VariableDecls) lambda.getParamSet().getParams().get(0)).getTypeExpr() == null) {
            l = l.withParamSet(lambda.getParamSet().withParenthesized(false));
        }
        return l;
    }
}
