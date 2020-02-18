package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Expression;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.op.UnwrapParentheses;

import java.util.List;

import static com.netflix.rewrite.tree.Tr.randomId;

@SuppressWarnings("RedundantCast")
public class SimplifyBooleanExpression extends RefactorVisitor {
    private int pass = 0;

    public SimplifyBooleanExpression() {
    }

    public SimplifyBooleanExpression(int pass) {
        this.pass = pass;
    }

    @Override
    public String getRuleName() {
        return "checkstyle.SimplifyBooleanExpression";
    }

    @Override
    public List<AstTransform> visitCompilationUnit(Tr.CompilationUnit cu) {
        List<AstTransform> changes = super.visitCompilationUnit(cu);
        if(!changes.isEmpty()) {
            changes.addAll(transform(cu, c -> {
                // add this in a transform step so that it happens after all parentheses unwrapping
                andThen(new SimplifyBooleanExpression(pass+1));
                return c;
            }));
        }
        return changes;
    }

    @Override
    public List<AstTransform> visitBinary(Tr.Binary binary) {
        List<AstTransform> changes = super.visitBinary(binary);

        if (binary.getOperator() instanceof Tr.Binary.Operator.And) {
            if (isLiteralFalse(binary.getLeft())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            } else if (isLiteralFalse(binary.getRight())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if(binary.getLeft().printTrimmed().replaceAll("\\s", "").equals(
                    binary.getRight().printTrimmed().replaceAll("\\s", ""))) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        } else if (binary.getOperator() instanceof Tr.Binary.Operator.Or) {
            if (isLiteralTrue(binary.getLeft())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            } else if (isLiteralTrue(binary.getRight())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if (binary.getLeft().printTrimmed().replaceAll("\\s", "").equals(
                    binary.getRight().printTrimmed().replaceAll("\\s", ""))) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        } else if (binary.getOperator() instanceof Tr.Binary.Operator.Equal) {
            if (isLiteralTrue(binary.getLeft())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if (isLiteralTrue(binary.getRight())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        } else if (binary.getOperator() instanceof Tr.Binary.Operator.NotEqual) {
            if (isLiteralFalse(binary.getLeft())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if (isLiteralFalse(binary.getRight())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitUnary(Tr.Unary unary) {
        List<AstTransform> changes = super.visitUnary(unary);

        if(unary.getOperator() instanceof Tr.Unary.Operator.Not) {
            if (isLiteralTrue(unary.getExpr())) {
                changes.addAll(transform((Expression) unary, (u, cursor) -> {
                    maybeUnwrapParentheses(cursor);
                    return new Tr.Literal(randomId(), false, "false",
                            Type.Primitive.Boolean, unary.getFormatting());
                }));
            } else if (isLiteralFalse(unary.getExpr())) {
                changes.addAll(transform((Expression) unary, (u, cursor) -> {
                    maybeUnwrapParentheses(cursor);
                    return new Tr.Literal(randomId(), true, "true",
                            Type.Primitive.Boolean, unary.getFormatting());
                }));
            } else if(unary.getExpr() instanceof Tr.Unary && ((Tr.Unary) unary.getExpr()).getOperator() instanceof Tr.Unary.Operator.Not) {
                changes.addAll(transform((Expression) unary, (u, cursor) -> {
                    maybeUnwrapParentheses(cursor);
                    return ((Tr.Unary) unary.getExpr()).getExpr().withFormatting(unary.getFormatting());
                }));
            }
        }

        return changes;
    }

    private List<AstTransform> binaryLeftAndUnwrap(Tr.Binary binary) {
        return transform((Expression) binary, (b, cursor) -> {
            Tr.Binary b2 = (Tr.Binary) b;
            maybeUnwrapParentheses(cursor);
            return b2.getLeft().withFormatting(b2.getFormatting());
        });
    }

    private List<AstTransform> binaryRightAndUnwrap(Tr.Binary binary) {
        return transform((Expression) binary, (b, cursor) -> {
            Tr.Binary b2 = (Tr.Binary) b;
            maybeUnwrapParentheses(cursor);
            return b2.getRight().withFormatting(b2.getFormatting());
        });
    }

    private void maybeUnwrapParentheses(Cursor cursor) {
        if (cursor.getParentOrThrow().getTree() instanceof Tr.Parentheses) {
            andThen(new UnwrapParentheses(cursor.getParentOrThrow().getTree().getId()));
        }
    }

    private boolean isLiteralTrue(Expression expression) {
        return expression instanceof Tr.Literal && ((Tr.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    private boolean isLiteralFalse(Expression expression) {
        return expression instanceof Tr.Literal && ((Tr.Literal) expression).getValue() == Boolean.valueOf(false);
    }
}
