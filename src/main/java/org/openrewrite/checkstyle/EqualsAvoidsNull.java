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
package org.openrewrite.checkstyle;

import org.openrewrite.Tree;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static java.util.Collections.singletonList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.stripPrefix;

@AutoConfigure
public class EqualsAvoidsNull extends CheckstyleRefactorVisitor {
    private static final MethodMatcher STRING_EQUALS = new MethodMatcher("String equals(java.lang.Object)");
    private static final MethodMatcher STRING_EQUALS_IGNORE_CASE = new MethodMatcher("String equalsIgnoreCase(java.lang.String)");

    private boolean ignoreEqualsIgnoreCase;

    public EqualsAvoidsNull() {
        setCursoringOn();
    }

    @Override
    protected void configure(Module m) {
        this.ignoreEqualsIgnoreCase = m.prop("ignoreEqualsIgnoreCase", false);
    }

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
                        andThen(new RemoveUnnecessaryNullCheck(binary));
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

    private static class RemoveUnnecessaryNullCheck extends JavaRefactorVisitor {
        private final J.Binary scope;

        public RemoveUnnecessaryNullCheck(J.Binary scope) {
            this.scope = scope;
            setCursoringOn();
        }

        @Override
        public J visitBinary(J.Binary binary) {
            maybeUnwrapParentheses(getCursor().getParent());

            if (scope.isScope(binary)) {
                return stripPrefix(binary.getRight());
            }

            return super.visitBinary(binary);
        }
    }
}
