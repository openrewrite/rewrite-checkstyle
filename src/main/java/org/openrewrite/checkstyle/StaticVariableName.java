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

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.ChangeFieldName;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.function.Function;
import java.util.regex.Pattern;

@AutoConfigure
public class StaticVariableName extends CheckstyleRefactorVisitor {
    // TODO should this be configurable?
    private static final Function<String, String> renamer = StaticVariableName::snakeCaseToCamel;

    private Pattern format;
    private boolean applyToPublic;
    private boolean applyToProtected;
    private boolean applyToPackage;
    private boolean applyToPrivate;

    public StaticVariableName() {
        setCursoringOn();
    }

    @Override
    protected void configure(Module m) {
        this.format = m.prop("format", Pattern.compile("^[a-z][a-zA-Z0-9]*$"));
        this.applyToPublic = m.prop("applyToPublic", true);
        this.applyToProtected = m.prop("applyToProtected", true);
        this.applyToPackage = m.prop("applyToPackage", true);
        this.applyToPrivate = m.prop("applyToPrivate", true);
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        J.VariableDecls multiVariable = getCursor().getParentOrThrow().getTree();
        if (multiVariable.hasModifier("static") && !format.matcher(variable.getSimpleName()).matches() && (
                (applyToPublic && multiVariable.hasModifier("public")) ||
                        (applyToProtected && multiVariable.hasModifier("protected")) ||
                        (applyToPrivate && multiVariable.hasModifier("private")) ||
                        (applyToPackage && (!multiVariable.hasModifier("public") && !multiVariable.hasModifier("protected") && !multiVariable.hasModifier("private")))
        )) {
            JavaType.Class containingClassType = TypeUtils.asClass(enclosingClass().getType());
            andThen(new ChangeFieldName.Scoped(containingClassType, variable.getSimpleName(), renamer.apply(variable.getSimpleName())));
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
