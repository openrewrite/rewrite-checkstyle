package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.ParenthesesToken;
import org.openrewrite.tree.Expression;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.tree.Type;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.op.UnwrapParentheses;

import java.util.List;
import java.util.Set;

import static org.gradle.rewrite.checkstyle.policy.ParenthesesToken.*;

@Builder
public class UnnecessaryParentheses extends RefactorVisitor {
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
    public String getRuleName() {
        return "checkstyle.UnnecessaryParentheses";
    }

    @Override
    public <T extends Tree> List<AstTransform> visitParentheses(J.Parentheses<T> parens) {
        T insideParens = parens.getTree();
        if (insideParens instanceof J.Ident && tokens.contains(IDENT)) {
            andThen(new UnwrapParentheses(parens.getId()));
        } else if (insideParens instanceof J.Literal) {
            J.Literal tree = (J.Literal) insideParens;
            Type.Primitive type = tree.getType();
            if ((tokens.contains(NUM_INT) && type == Type.Primitive.Int) ||
                    (tokens.contains(NUM_DOUBLE) && type == Type.Primitive.Double) ||
                    (tokens.contains(NUM_LONG) && type == Type.Primitive.Long) ||
                    (tokens.contains(NUM_FLOAT) && type == Type.Primitive.Float) ||
                    (tokens.contains(STRING_LITERAL) && type == Type.Primitive.String) ||
                    (tokens.contains(LITERAL_FALSE) && type == Type.Primitive.Boolean && tree.getValue() == Boolean.valueOf(false)) ||
                    (tokens.contains(LITERAL_TRUE) && type == Type.Primitive.Boolean && tree.getValue() == Boolean.valueOf(true))) {

                andThen(new UnwrapParentheses(parens.getId()));
            }
        } else if (insideParens instanceof J.Binary && tokens.contains(EXPR)) {
            Tree parent = getCursor().getParentOrThrow().getTree();
            if (!(parent instanceof J.Binary || parent instanceof J.InstanceOf || parent instanceof J.Unary) ||
                    (isSameOperator(parent, insideParens) || getPrecedence(parent) > getPrecedence(insideParens))) {
                andThen(new UnwrapParentheses(parens.getId()));
            }
        }

        return super.visitParentheses(parens);
    }

    private boolean isSameOperator(Tree t1, Tree t2) {
        return getOperator(t1).getClass().equals(getOperator(t2).getClass());
    }

    private Object getOperator(Tree t) {
        if (t instanceof J.Binary) {
            return ((J.Binary) t).getOperator();
        } else if (t instanceof J.Unary) {
            return ((J.Unary) t).getOperator();
        } else if (t instanceof J.InstanceOf) {
            return t;
        }
        throw new IllegalStateException("Expected either a J.Binary, J.Unary, or J.InstanceOf");
    }

    private int getPrecedence(Tree tree) {
        if (tree instanceof J.InstanceOf) {
            return 7;
        } else if (tree instanceof J.Unary) {
            J.Unary.Operator op = ((J.Unary) tree).getOperator();
            if (op instanceof J.Unary.Operator.PostIncrement || op instanceof J.Unary.Operator.PostDecrement) {
                // post-increment and post-decrement are non-associative
                return 1;
            } else {
                // every other unary operator is right-associative
                return 2;
            }
        } else if (tree instanceof J.Binary) {
            J.Binary.Operator op = ((J.Binary) tree).getOperator();
            if (op instanceof J.Binary.Operator.Multiplication ||
                    op instanceof J.Binary.Operator.Division ||
                    op instanceof J.Binary.Operator.Modulo) {
                return 4;
            } else if (op instanceof J.Binary.Operator.Addition ||
                    op instanceof J.Binary.Operator.Subtraction) {
                return 5;
            } else if (op instanceof J.Binary.Operator.LeftShift ||
                    op instanceof J.Binary.Operator.RightShift ||
                    op instanceof J.Binary.Operator.UnsignedRightShift) {
                return 6;
            } else if (op instanceof J.Binary.Operator.LessThan ||
                    op instanceof J.Binary.Operator.LessThanOrEqual ||
                    op instanceof J.Binary.Operator.GreaterThan ||
                    op instanceof J.Binary.Operator.GreaterThanOrEqual) {
                return 7;
            } else if (op instanceof J.Binary.Operator.Equal ||
                    op instanceof J.Binary.Operator.NotEqual) {
                return 8;
            } else if (op instanceof J.Binary.Operator.BitAnd) {
                return 9;
            } else if (op instanceof J.Binary.Operator.BitXor) {
                return 10;
            } else if (op instanceof J.Binary.Operator.BitOr) {
                return 11;
            } else if (op instanceof J.Binary.Operator.And) {
                return 12;
            } else if (op instanceof J.Binary.Operator.Or) {
                return 13;
            }
        }

        return 0;
    }

    @Override
    public List<AstTransform> visitAssignOp(J.AssignOp assignOp) {
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

            andThen(new UnwrapParentheses(assignment.getId()));
        }

        return super.visitAssignOp(assignOp);
    }

    @Override
    public List<AstTransform> visitLambda(J.Lambda lambda) {
        return maybeTransform(lambda,
                lambda.getParamSet().getParams().size() == 1 &&
                        lambda.getParamSet().isParenthesized() &&
                        lambda.getParamSet().getParams().get(0) instanceof J.VariableDecls &&
                        ((J.VariableDecls) lambda.getParamSet().getParams().get(0)).getTypeExpr() == null,
                super::visitLambda,
                l -> l.withParamSet(lambda.getParamSet().withParenthesized(false))
        );
    }
}
