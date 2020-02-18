package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Expression;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.visitor.MethodMatcher;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.ScopedRefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.op.UnwrapParentheses;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.UUID;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Formatting.stripPrefix;
import static java.util.Collections.singletonList;

@RequiredArgsConstructor
public class EqualsAvoidsNull extends RefactorVisitor {
    private static final MethodMatcher STRING_EQUALS = new MethodMatcher("String equals(java.lang.Object)");
    private static final MethodMatcher STRING_EQUALS_IGNORE_CASE = new MethodMatcher("String equalsIgnoreCase(java.lang.String)");

    private final boolean ignoreEqualsIgnoreCase;

    @Override
    public String getRuleName() {
        return "EqualsAvoidsNull";
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public List<AstTransform> visitMethodInvocation(Tr.MethodInvocation method) {
        if (STRING_EQUALS.matches(method) || (!ignoreEqualsIgnoreCase && STRING_EQUALS_IGNORE_CASE.matches(method)) &&
                method.getArgs().getArgs().get(0) instanceof Tr.Literal && !(method.getSelect() instanceof Tr.Literal)) {
            Tree parent = getCursor().getParentOrThrow().getTree();
            if (parent instanceof Tr.Binary) {
                Tr.Binary binary = (Tr.Binary) parent;
                if (binary.getOperator() instanceof Tr.Binary.Operator.And && binary.getLeft() instanceof Tr.Binary) {
                    Tr.Binary potentialNullCheck = (Tr.Binary) binary.getLeft();
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
        return expression instanceof Tr.Literal && ((Tr.Literal) expression).getType() == Type.Primitive.Null;
    }

    private boolean matchesSelect(Expression expression, Expression select) {
        return expression.printTrimmed().replaceAll("\\s", "").equals(select.printTrimmed().replaceAll("\\s", ""));
    }

    private static class RemoveUnnecessaryNullCheck extends ScopedRefactorVisitor {
        public RemoveUnnecessaryNullCheck(UUID scope) {
            super(scope);
        }

        @Override
        public List<AstTransform> visitBinary(Tr.Binary binary) {
            Tree parent = getCursor().getParentOrThrow().getTree();
            if (parent instanceof Tr.Parentheses) {
                andThen(new UnwrapParentheses(parent.getId()));
            }

            return maybeTransform(binary,
                    binary.getId().equals(scope),
                    super::visitBinary,
                    b -> (Expression) b,
                    b -> stripPrefix(((Tr.Binary) b).getRight())
            );
        }
    }
}
