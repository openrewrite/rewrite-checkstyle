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
import org.openrewrite.java.refactor.ChangeFieldName;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Fix for <a href="https://checkstyle.sourceforge.io/config_naming.html#StaticVariableName">StaticVariableName</a>.
 */
public class StaticVariableName extends CheckstyleRefactorVisitor {
    // TODO should this be configurable?
    private static final Function<String, String> renamer = StaticVariableName::snakeCaseToCamel;

    private final Pattern format;
    private final boolean applyToPublic;
    private final boolean applyToProtected;
    private final boolean applyToPackage;
    private final boolean applyToPrivate;

    public StaticVariableName(Pattern format, boolean applyToPublic, boolean applyToProtected, boolean applyToPackage, boolean applyToPrivate) {
        super("checkstyle.StaticVariableName");
        this.format = format;
        this.applyToPublic = applyToPublic;
        this.applyToProtected = applyToProtected;
        this.applyToPackage = applyToPackage;
        this.applyToPrivate = applyToPrivate;
        setCursoringOn();
    }

    @AutoConfigure
    public static StaticVariableName configure(Config config) {
        return fromModule(
                config,
                "StaticVariableName",
                m -> new StaticVariableName(
                        m.prop("format", Pattern.compile("^[a-z][a-zA-Z0-9]*$")),
                        m.prop("applyToPublic", true),
                        m.prop("applyToProtected", true),
                        m.prop("applyToPackage", true),
                        m.prop("applyToPrivate", true)
                )
        );
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
