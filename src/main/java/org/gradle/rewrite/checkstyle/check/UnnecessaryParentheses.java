package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Expression;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.op.UnwrapParentheses;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.ParenthesesToken;

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
    public <T extends Tree> List<AstTransform> visitParentheses(Tr.Parentheses<T> parens) {
        T insideParens = parens.getTree();
        if (insideParens instanceof Tr.Ident && tokens.contains(IDENT)) {
            andThen(new UnwrapParentheses(parens.getId()));
        } else if (insideParens instanceof Tr.Literal) {
            Tr.Literal tree = (Tr.Literal) insideParens;
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
        } else if (insideParens instanceof Tr.Binary && tokens.contains(EXPR)) {
            Tree parent = getCursor().getParentOrThrow().getTree();
            if (!(parent instanceof Tr.Binary || parent instanceof Tr.InstanceOf || parent instanceof Tr.Unary) ||
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
        if (t instanceof Tr.Binary) {
            return ((Tr.Binary) t).getOperator();
        } else if (t instanceof Tr.Unary) {
            return ((Tr.Unary) t).getOperator();
        } else if (t instanceof Tr.InstanceOf) {
            return t;
        }
        throw new IllegalStateException("Expected either a Tr.Binary, Tr.Unary, or Tr.InstanceOf");
    }

    private int getPrecedence(Tree tree) {
        if (tree instanceof Tr.InstanceOf) {
            return 7;
        } else if (tree instanceof Tr.Unary) {
            Tr.Unary.Operator op = ((Tr.Unary) tree).getOperator();
            if (op instanceof Tr.Unary.Operator.PostIncrement || op instanceof Tr.Unary.Operator.PostDecrement) {
                // post-increment and post-decrement are non-associative
                return 1;
            } else {
                // every other unary operator is right-associative
                return 2;
            }
        } else if (tree instanceof Tr.Binary) {
            Tr.Binary.Operator op = ((Tr.Binary) tree).getOperator();
            if (op instanceof Tr.Binary.Operator.Multiplication ||
                    op instanceof Tr.Binary.Operator.Division ||
                    op instanceof Tr.Binary.Operator.Modulo) {
                return 4;
            } else if (op instanceof Tr.Binary.Operator.Addition ||
                    op instanceof Tr.Binary.Operator.Subtraction) {
                return 5;
            } else if (op instanceof Tr.Binary.Operator.LeftShift ||
                    op instanceof Tr.Binary.Operator.RightShift ||
                    op instanceof Tr.Binary.Operator.UnsignedRightShift) {
                return 6;
            } else if (op instanceof Tr.Binary.Operator.LessThan ||
                    op instanceof Tr.Binary.Operator.LessThanOrEqual ||
                    op instanceof Tr.Binary.Operator.GreaterThan ||
                    op instanceof Tr.Binary.Operator.GreaterThanOrEqual) {
                return 7;
            } else if (op instanceof Tr.Binary.Operator.Equal ||
                    op instanceof Tr.Binary.Operator.NotEqual) {
                return 8;
            } else if (op instanceof Tr.Binary.Operator.BitAnd) {
                return 9;
            } else if (op instanceof Tr.Binary.Operator.BitXor) {
                return 10;
            } else if (op instanceof Tr.Binary.Operator.BitOr) {
                return 11;
            } else if (op instanceof Tr.Binary.Operator.And) {
                return 12;
            } else if (op instanceof Tr.Binary.Operator.Or) {
                return 13;
            }
        }

        return 0;
    }

    @Override
    public List<AstTransform> visitAssignOp(Tr.AssignOp assignOp) {
        Expression assignment = assignOp.getAssignment();
        Tr.AssignOp.Operator op = assignOp.getOperator();
        if (assignment instanceof Tr.Parentheses && ((tokens.contains(BAND_ASSIGN) && op instanceof Tr.AssignOp.Operator.BitAnd) ||
                (tokens.contains(BOR_ASSIGN) && op instanceof Tr.AssignOp.Operator.BitOr) ||
                (tokens.contains(BSR_ASSIGN) && op instanceof Tr.AssignOp.Operator.UnsignedRightShift) ||
                (tokens.contains(BXOR_ASSIGN) && op instanceof Tr.AssignOp.Operator.BitXor) ||
                (tokens.contains(SR_ASSIGN) && op instanceof Tr.AssignOp.Operator.RightShift) ||
                (tokens.contains(SL_ASSIGN) && op instanceof Tr.AssignOp.Operator.LeftShift) ||
                (tokens.contains(MINUS_ASSIGN) && op instanceof Tr.AssignOp.Operator.Subtraction) ||
                (tokens.contains(DIV_ASSIGN) && op instanceof Tr.AssignOp.Operator.Division) ||
                (tokens.contains(PLUS_ASSIGN) && op instanceof Tr.AssignOp.Operator.Addition) ||
                (tokens.contains(STAR_ASSIGN) && op instanceof Tr.AssignOp.Operator.Multiplication) ||
                (tokens.contains(MOD_ASSIGN) && op instanceof Tr.AssignOp.Operator.Modulo))) {

            andThen(new UnwrapParentheses(assignment.getId()));
        }

        return super.visitAssignOp(assignOp);
    }

    @Override
    public List<AstTransform> visitLambda(Tr.Lambda lambda) {
        return maybeTransform(lambda,
                lambda.getParamSet().getParams().size() == 1 &&
                        lambda.getParamSet().isParenthesized() &&
                        lambda.getParamSet().getParams().get(0) instanceof Tr.VariableDecls &&
                        ((Tr.VariableDecls) lambda.getParamSet().getParams().get(0)).getTypeExpr() == null,
                super::visitLambda,
                l -> l.withParamSet(lambda.getParamSet().withParenthesized(false))
        );
    }
}
