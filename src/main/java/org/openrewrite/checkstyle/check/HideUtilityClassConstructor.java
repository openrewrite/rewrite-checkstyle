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

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class HideUtilityClassConstructor extends CheckstyleRefactorVisitor {
    public HideUtilityClassConstructor() {
        super("checkstyle.HideUtilityClassConstructor");
    }

    @AutoConfigure
    public static HideUtilityClassConstructor configure(Config config) {
        return fromModule(config, "HideUtilityClassConstructor", m -> new HideUtilityClassConstructor());
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        if (classDecl.getBody().getStatements().stream()
                .allMatch(s -> !(s instanceof J.MethodDecl) ||
                        !((J.MethodDecl) s).isConstructor() ||
                        !((J.MethodDecl) s).hasModifier("static"))) {
            c = c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream().map(s -> {
                J.MethodDecl ctor = (J.MethodDecl) s;

                if (ctor.isConstructor() && !ctor.hasModifier("private")) {
                    List<J.Modifier> modifiers = ctor.getModifiers();

                    int insertPosition = 0;
                    for (int i = 0; i < modifiers.size(); i++) {
                        J.Modifier modifier = modifiers.get(i);
                        if (modifier instanceof J.Modifier.Public) {
                            insertPosition = i;
                        }
                    }

                    modifiers.set(insertPosition, new J.Modifier.Private(randomId(), modifiers.get(insertPosition).getFormatting()));

                    return ctor.withModifiers(modifiers);
                }

                return ctor;
            }).collect(toList())));
        }

        return c;
    }
}
