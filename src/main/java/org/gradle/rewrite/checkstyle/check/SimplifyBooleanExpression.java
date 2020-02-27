package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.Cursor;
import org.openrewrite.tree.Expression;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Type;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.op.UnwrapParentheses;

import java.util.List;

import static org.openrewrite.tree.J.randomId;

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
    public List<AstTransform> visitCompilationUnit(J.CompilationUnit cu) {
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
    public List<AstTransform> visitBinary(J.Binary binary) {
        List<AstTransform> changes = super.visitBinary(binary);

        if (binary.getOperator() instanceof J.Binary.Operator.And) {
            if (isLiteralFalse(binary.getLeft())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            } else if (isLiteralFalse(binary.getRight())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if(binary.getLeft().printTrimmed().replaceAll("\\s", "").equals(
                    binary.getRight().printTrimmed().replaceAll("\\s", ""))) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        } else if (binary.getOperator() instanceof J.Binary.Operator.Or) {
            if (isLiteralTrue(binary.getLeft())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            } else if (isLiteralTrue(binary.getRight())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if (binary.getLeft().printTrimmed().replaceAll("\\s", "").equals(
                    binary.getRight().printTrimmed().replaceAll("\\s", ""))) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        } else if (binary.getOperator() instanceof J.Binary.Operator.Equal) {
            if (isLiteralTrue(binary.getLeft())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if (isLiteralTrue(binary.getRight())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        } else if (binary.getOperator() instanceof J.Binary.Operator.NotEqual) {
            if (isLiteralFalse(binary.getLeft())) {
                changes.addAll(binaryRightAndUnwrap(binary));
            } else if (isLiteralFalse(binary.getRight())) {
                changes.addAll(binaryLeftAndUnwrap(binary));
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitUnary(J.Unary unary) {
        List<AstTransform> changes = super.visitUnary(unary);

        if(unary.getOperator() instanceof J.Unary.Operator.Not) {
            if (isLiteralTrue(unary.getExpr())) {
                changes.addAll(transform((Expression) unary, (u, cursor) -> {
                    maybeUnwrapParentheses(cursor);
                    return new J.Literal(randomId(), false, "false",
                            Type.Primitive.Boolean, unary.getFormatting());
                }));
            } else if (isLiteralFalse(unary.getExpr())) {
                changes.addAll(transform((Expression) unary, (u, cursor) -> {
                    maybeUnwrapParentheses(cursor);
                    return new J.Literal(randomId(), true, "true",
                            Type.Primitive.Boolean, unary.getFormatting());
                }));
            } else if(unary.getExpr() instanceof J.Unary && ((J.Unary) unary.getExpr()).getOperator() instanceof J.Unary.Operator.Not) {
                changes.addAll(transform((Expression) unary, (u, cursor) -> {
                    maybeUnwrapParentheses(cursor);
                    return ((J.Unary) unary.getExpr()).getExpr().withFormatting(unary.getFormatting());
                }));
            }
        }

        return changes;
    }

    private List<AstTransform> binaryLeftAndUnwrap(J.Binary binary) {
        return transform((Expression) binary, (b, cursor) -> {
            J.Binary b2 = (J.Binary) b;
            maybeUnwrapParentheses(cursor);
            return b2.getLeft().withFormatting(b2.getFormatting());
        });
    }

    private List<AstTransform> binaryRightAndUnwrap(J.Binary binary) {
        return transform((Expression) binary, (b, cursor) -> {
            J.Binary b2 = (J.Binary) b;
            maybeUnwrapParentheses(cursor);
            return b2.getRight().withFormatting(b2.getFormatting());
        });
    }

    private void maybeUnwrapParentheses(Cursor cursor) {
        if (cursor.getParentOrThrow().getTree() instanceof J.Parentheses) {
            andThen(new UnwrapParentheses(cursor.getParentOrThrow().getTree().getId()));
        }
    }

    private boolean isLiteralTrue(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    private boolean isLiteralFalse(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
    }
}
