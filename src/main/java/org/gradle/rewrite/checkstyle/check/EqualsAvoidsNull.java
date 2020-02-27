package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.Expression;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.tree.Type;
import org.openrewrite.visitor.MethodMatcher;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.ScopedRefactorVisitor;
import org.openrewrite.visitor.refactor.op.UnwrapParentheses;

import java.util.List;
import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.openrewrite.tree.Formatting.EMPTY;
import static org.openrewrite.tree.Formatting.stripPrefix;

public class EqualsAvoidsNull extends RefactorVisitor {
    private static final MethodMatcher STRING_EQUALS = new MethodMatcher("String equals(java.lang.Object)");
    private static final MethodMatcher STRING_EQUALS_IGNORE_CASE = new MethodMatcher("String equalsIgnoreCase(java.lang.String)");

    private final boolean ignoreEqualsIgnoreCase;

    public EqualsAvoidsNull(boolean ignoreEqualsIgnoreCase) {
        this.ignoreEqualsIgnoreCase = ignoreEqualsIgnoreCase;
    }

    public EqualsAvoidsNull() {
        this(false);
    }

    @Override
    public String getRuleName() {
        return "checkstyle.EqualsAvoidsNull";
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public List<AstTransform> visitMethodInvocation(J.MethodInvocation method) {
        if ((STRING_EQUALS.matches(method) || (!ignoreEqualsIgnoreCase && STRING_EQUALS_IGNORE_CASE.matches(method))) &&
                method.getArgs().getArgs().get(0) instanceof J.Literal &&
                !(method.getSelect() instanceof J.Literal)) {
            Tree parent = getCursor().getParentOrThrow().getTree();
            if (parent instanceof J.Binary) {
                J.Binary binary = (J.Binary) parent;
                if (binary.getOperator() instanceof J.Binary.Operator.And && binary.getLeft() instanceof J.Binary) {
                    J.Binary potentialNullCheck = (J.Binary) binary.getLeft();
                    if ((isNullLiteral(potentialNullCheck.getLeft()) && matchesSelect(potentialNullCheck.getRight(), method.getSelect())) ||
                            (isNullLiteral(potentialNullCheck.getRight()) && matchesSelect(potentialNullCheck.getLeft(), method.getSelect()))) {
                        andThen(new RemoveUnnecessaryNullCheck(binary.getId()));
                    }
                }
            }

            return maybeTransform(method,
                    true,
                    super::visitMethodInvocation,
                    m -> m.withSelect(m.getArgs().getArgs().get(0).withFormatting(m.getSelect().getFormatting()))
                            .withArgs(m.getArgs().withArgs(singletonList(m.getSelect().withFormatting(EMPTY))))
            );
        }

        return super.visitMethodInvocation(method);
    }

    private boolean isNullLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == Type.Primitive.Null;
    }

    private boolean matchesSelect(Expression expression, Expression select) {
        return expression.printTrimmed().replaceAll("\\s", "").equals(select.printTrimmed().replaceAll("\\s", ""));
    }

    private static class RemoveUnnecessaryNullCheck extends ScopedRefactorVisitor {
        public RemoveUnnecessaryNullCheck(UUID scope) {
            super(scope);
        }

        @Override
        public List<AstTransform> visitBinary(J.Binary binary) {
            Tree parent = getCursor().getParentOrThrow().getTree();
            if (parent instanceof J.Parentheses) {
                andThen(new UnwrapParentheses(parent.getId()));
            }

            return maybeTransform(binary,
                    binary.getId().equals(scope),
                    super::visitBinary,
                    b -> (Expression) b,
                    b -> stripPrefix(((J.Binary) b).getRight())
            );
        }
    }
}
