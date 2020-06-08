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

import org.openrewrite.Formatting;
import org.openrewrite.AutoConfigure;
import org.openrewrite.java.tree.J;

import java.util.List;

import static org.openrewrite.Formatting.format;
import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class FinalClass extends CheckstyleRefactorVisitor {

    @Override
    public boolean isIdempotent() {
        return false;
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        if(classDecl.getBody().getStatements().stream()
                .noneMatch(s -> s instanceof J.MethodDecl &&
                        ((J.MethodDecl) s).isConstructor() &&
                        !((J.MethodDecl) s).hasModifier("private"))) {
            List<J.Modifier> modifiers = c.getModifiers();

            int insertPosition = 0;
            for (int i = 0; i < modifiers.size(); i++) {
                J.Modifier modifier = modifiers.get(i);
                if (modifier instanceof J.Modifier.Public || modifier instanceof J.Modifier.Static) {
                    insertPosition = i + 1;
                }
            }

            Formatting format = format(" ");
            if (insertPosition == 0 && !modifiers.isEmpty()) {
                format = modifiers.get(0).getFormatting();
                formatFirstPrefix(modifiers, " ");
            }

            modifiers.add(insertPosition, new J.Modifier.Final(randomId(), format));

            c = c.withModifiers(modifiers);
        }

        return c;
    }
}
