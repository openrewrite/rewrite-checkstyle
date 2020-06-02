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
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import static java.util.stream.Collectors.toList;

public class NoFinalizer extends CheckstyleRefactorVisitor {
    public NoFinalizer() {
        super("checkstyle.NoFinalizer");
    }

    @AutoConfigure
    public static NoFinalizer configure(Config config) {
        return fromModule(config, "NoFinalizer", m -> new NoFinalizer());
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        return classDecl.getMethods().stream()
                .filter(method -> method.getSimpleName().equals("finalize") &&
                        method.getReturnTypeExpr() != null &&
                        JavaType.Primitive.Void.equals(method.getReturnTypeExpr().getType()) &&
                        method.getParams().getParams().stream().allMatch(p -> p instanceof J.Empty))
                .findAny()
                .map(m -> c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                        .filter(s -> !s.getId().equals(m.getId()))
                        .collect(toList())))
                )
                .orElse(c);
    }
}
