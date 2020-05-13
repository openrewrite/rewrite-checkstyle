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

import lombok.Builder;
import org.openrewrite.java.refactor.ChangeFieldName;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.function.Function;

/**
 * Fix for <a href="https://checkstyle.sourceforge.io/config_naming.html#StaticVariableName">StaticVariableName</a>.
 */
@Builder
public class StaticVariableName extends JavaRefactorVisitor {
    @Builder.Default
    private String format = "^[a-z][a-zA-Z0-9]*$";

    @Builder.Default
    private final Function<String, String> renamer = StaticVariableName::snakeCaseToCamel;

    @Builder.Default
    private final boolean applyToPublic = true;

    @Builder.Default
    private final boolean applyToProtected = true;

    @Builder.Default
    private final boolean applyToPackage = true;

    @Builder.Default
    private final boolean applyToPrivate = true;

    @Override
    public String getName() {
        return "checkstyle.StaticVariableName";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        J.VariableDecls multiVariable = getCursor().getParentOrThrow().getTree();
        if(multiVariable.hasModifier("static") && (
                (applyToPublic && multiVariable.hasModifier("public")) ||
                        (applyToProtected && multiVariable.hasModifier("protected")) ||
                        (applyToPrivate && multiVariable.hasModifier("private")) ||
                        (applyToPackage && (!multiVariable.hasModifier("public") && !multiVariable.hasModifier("protected") && !multiVariable.hasModifier("private")))
        )) {
            JavaType.Class containingClassType = TypeUtils.asClass(enclosingClass().getType());
            andThen(new ChangeFieldName(containingClassType, variable.getSimpleName(), renamer.apply(variable.getSimpleName())));
        }
        return super.visitVariable(variable);
    }

    static String snakeCaseToCamel(String value) {
        if (!value.matches("([A-Z0-9]+_*)+")) {
            return value;
        }

        StringBuilder camelName = new StringBuilder();
        char last = 0;
        for (char c : value.toCharArray()) {
            if (c != '_') {
                camelName.append(last == '_' ? c : Character.toLowerCase(c));
            }
            last = c;
        }
        return camelName.toString();
    }
}
