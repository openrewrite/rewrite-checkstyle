package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Tree;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;
import org.openrewrite.java.visitor.refactor.ScopedJavaRefactorVisitor;
import org.openrewrite.java.visitor.refactor.UnwrapParentheses;

import java.util.UUID;

import static java.util.Collections.singletonList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.stripPrefix;

public class EqualsAvoidsNull extends JavaRefactorVisitor {
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
    public String getName() {
        return "checkstyle.EqualsAvoidsNull";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);

        if ((STRING_EQUALS.matches(m) || (!ignoreEqualsIgnoreCase && STRING_EQUALS_IGNORE_CASE.matches(m))) &&
                m.getArgs().getArgs().get(0) instanceof J.Literal &&
                !(m.getSelect() instanceof J.Literal)) {
            Tree parent = getCursor().getParentOrThrow().getTree();
            if (parent instanceof J.Binary) {
                J.Binary binary = (J.Binary) parent;
                if (binary.getOperator() instanceof J.Binary.Operator.And && binary.getLeft() instanceof J.Binary) {
                    J.Binary potentialNullCheck = (J.Binary) binary.getLeft();
                    if ((isNullLiteral(potentialNullCheck.getLeft()) && matchesSelect(potentialNullCheck.getRight(), m.getSelect())) ||
                            (isNullLiteral(potentialNullCheck.getRight()) && matchesSelect(potentialNullCheck.getLeft(), m.getSelect()))) {
                        andThen(new RemoveUnnecessaryNullCheck(binary.getId()));
                    }
                }
            }

            m = m.withSelect(m.getArgs().getArgs().get(0).withFormatting(m.getSelect().getFormatting()))
                    .withArgs(m.getArgs().withArgs(singletonList(m.getSelect().withFormatting(EMPTY))));
        }

        return m;
    }

    private boolean isNullLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == JavaType.Primitive.Null;
    }

    private boolean matchesSelect(Expression expression, Expression select) {
        return expression.printTrimmed().replaceAll("\\s", "").equals(select.printTrimmed().replaceAll("\\s", ""));
    }

    private static class RemoveUnnecessaryNullCheck extends ScopedJavaRefactorVisitor {
        public RemoveUnnecessaryNullCheck(UUID scope) {
            super(scope);
        }

        @Override
        public J visitBinary(J.Binary binary) {
            Tree parent = getCursor().getParentOrThrow().getTree();

            if (parent instanceof J.Parentheses) {
                andThen(new UnwrapParentheses(parent.getId()));
            }

            if (isScope()) {
                return stripPrefix(binary.getRight());
            }

            return super.visitBinary(binary);
        }
    }
}
