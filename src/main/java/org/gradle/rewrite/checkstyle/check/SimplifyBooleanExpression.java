package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Tree;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;
import org.openrewrite.java.visitor.refactor.UnwrapParentheses;

import static org.openrewrite.Tree.randomId;

public class SimplifyBooleanExpression extends JavaRefactorVisitor {
    private int pass = 0;

    public SimplifyBooleanExpression() {
    }

    public SimplifyBooleanExpression(int pass) {
        this.pass = pass;
    }

    @Override
    public String getName() {
        return "checkstyle.SimplifyBooleanExpression";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        J.CompilationUnit c = refactor(cu, super::visitCompilationUnit);
        if (c != cu) {
            andThen(new SimplifyBooleanExpression(pass + 1));
        }
        return c;
    }

    @Override
    public J visitBinary(J.Binary binary) {
        J.Binary b = refactor(binary, super::visitBinary);

        if (b.getOperator() instanceof J.Binary.Operator.And) {
            if (isLiteralFalse(b.getLeft())) {
                return binaryLeftAndUnwrap(b);
            } else if (isLiteralFalse(b.getRight())) {
                return binaryRightAndUnwrap(b);
            } else if (b.getLeft().printTrimmed().replaceAll("\\s", "").equals(
                    b.getRight().printTrimmed().replaceAll("\\s", ""))) {
                return binaryLeftAndUnwrap(b);
            }
        } else if (b.getOperator() instanceof J.Binary.Operator.Or) {
            if (isLiteralTrue(b.getLeft())) {
                return binaryLeftAndUnwrap(b);
            } else if (isLiteralTrue(b.getRight())) {
                return binaryRightAndUnwrap(b);
            } else if (b.getLeft().printTrimmed().replaceAll("\\s", "").equals(
                    b.getRight().printTrimmed().replaceAll("\\s", ""))) {
                return binaryLeftAndUnwrap(b);
            }
        } else if (b.getOperator() instanceof J.Binary.Operator.Equal) {
            if (isLiteralTrue(b.getLeft())) {
                return binaryRightAndUnwrap(b);
            } else if (isLiteralTrue(b.getRight())) {
                return binaryLeftAndUnwrap(b);
            }
        } else if (b.getOperator() instanceof J.Binary.Operator.NotEqual) {
            if (isLiteralFalse(b.getLeft())) {
                return binaryRightAndUnwrap(b);
            } else if (isLiteralFalse(b.getRight())) {
                return binaryLeftAndUnwrap(b);
            }
        }

        return b;
    }

    @Override
    public J visitUnary(J.Unary unary) {
        J.Unary u = refactor(unary, super::visitUnary);

        if (u.getOperator() instanceof J.Unary.Operator.Not) {
            if (isLiteralTrue(u.getExpr())) {
                maybeUnwrapParentheses();
                return new J.Literal(randomId(), false, "false",
                        JavaType.Primitive.Boolean, u.getFormatting());
            } else if (isLiteralFalse(u.getExpr())) {
                maybeUnwrapParentheses();
                return new J.Literal(randomId(), true, "true",
                            JavaType.Primitive.Boolean, u.getFormatting());
            } else if (u.getExpr() instanceof J.Unary && ((J.Unary) u.getExpr()).getOperator() instanceof J.Unary.Operator.Not) {
                maybeUnwrapParentheses();
                return ((J.Unary) u.getExpr()).getExpr().withFormatting(u.getFormatting());
            }
        }

        return u;
    }

    private Expression binaryLeftAndUnwrap(J.Binary binary) {
        maybeUnwrapParentheses();
        return binary.getLeft().withFormatting(binary.getFormatting());
    }

    private Expression binaryRightAndUnwrap(J.Binary binary) {
        maybeUnwrapParentheses();
        return binary.getRight().withFormatting(binary.getFormatting());
    }

    private void maybeUnwrapParentheses() {
        Tree tree = getCursor().getParentOrThrow().getTree();
        if (tree instanceof J.Parentheses) {
            andThen(new UnwrapParentheses(tree.getId()));
        }
    }

    private boolean isLiteralTrue(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    private boolean isLiteralFalse(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
    }
}
