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
package org.openrewrite.checkstyle.check;

import org.eclipse.microprofile.config.Config;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Set;

import static java.util.Collections.singletonList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;

public class StringLiteralEquality extends CheckstyleRefactorVisitor {
    public StringLiteralEquality() {
        super("checkstyle.StringLiteralEquality");
    }

    @AutoConfigure
    public static StringLiteralEquality configure(Config config) {
        return fromModule(config, "StringLiteralEquality", m -> new StringLiteralEquality());
    }

    @Override
    public J visitBinary(J.Binary binary) {
        if(binary.getOperator() instanceof J.Binary.Operator.Equal && (
                isStringLiteral(binary.getLeft()) || isStringLiteral(binary.getRight()))) {
            Expression left = isStringLiteral(binary.getRight()) ? binary.getRight() : binary.getLeft();
            Expression right = isStringLiteral(binary.getRight()) ? binary.getLeft() : binary.getRight();

            return new J.MethodInvocation(randomId(),
                    left.withFormatting(EMPTY),
                    null,
                    J.Ident.build(randomId(), "equals", JavaType.Primitive.Boolean, EMPTY),
                    new J.MethodInvocation.Arguments(randomId(), singletonList(right.withFormatting(EMPTY)), EMPTY),
                    JavaType.Method.build(JavaType.Class.build("java.lang.Object"), "equals",
                            null, null, singletonList("o"),
                            Set.of(Flag.Public)),
                    binary.getFormatting());
        }

        return super.visitBinary(binary);
    }

    public boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == JavaType.Primitive.String;
    }
}
