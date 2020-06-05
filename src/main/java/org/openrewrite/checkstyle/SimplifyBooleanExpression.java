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

import org.eclipse.microprofile.config.Config;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class SimplifyBooleanExpression extends CheckstyleRefactorVisitor {
    public SimplifyBooleanExpression() {
        setCursoringOn();
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        J.CompilationUnit c = refactor(cu, super::visitCompilationUnit);
        if (c != cu) {
            andThen(new SimplifyBooleanExpression());
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
        maybeUnwrapParentheses(getCursor().getParent());
        return binary.getRight().withFormatting(binary.getFormatting());
    }

    private void maybeUnwrapParentheses() {
        maybeUnwrapParentheses(getCursor().getParent());
    }

    private boolean isLiteralTrue(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    private boolean isLiteralFalse(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
    }
}
