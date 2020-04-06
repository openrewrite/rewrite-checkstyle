package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.ParenthesesToken;
import org.openrewrite.Cursor;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Set;

import static org.gradle.rewrite.checkstyle.policy.ParenthesesToken.*;

@Builder
public class UnnecessaryParentheses extends JavaRefactorVisitor {
    @Builder.Default
    private final Set<ParenthesesToken> tokens = Set.of(
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

    @Override
    public String getName() {
        return "checkstyle.UnnecessaryParentheses";
    }

    @Override
    public boolean isCursored() {
        return true;
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
