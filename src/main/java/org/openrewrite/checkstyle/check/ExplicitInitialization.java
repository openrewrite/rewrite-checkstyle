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
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import static org.openrewrite.Formatting.formatLastSuffix;
import static org.openrewrite.Formatting.stripSuffix;

public class ExplicitInitialization extends CheckstyleRefactorVisitor {
    private final boolean onlyObjectReferences;

    public ExplicitInitialization(boolean onlyObjectReferences) {
        super("checkstyle.ExplicitInitialization");
        this.onlyObjectReferences = onlyObjectReferences;
        setCursoringOn();
    }

    @AutoConfigure
    public static ExplicitInitialization configure(Config config) {
        return fromModule(
                config,
                "ExplicitInitialization",
                m -> new ExplicitInitialization(m.prop("onlyObjectReferences", false))
        );
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        J.VariableDecls.NamedVar v = refactor(variable, super::visitVariable);

        Cursor variableDeclsCursor = getCursor().getParentOrThrow();
        if (!(variableDeclsCursor // J.VariableDecls
                .getParentOrThrow() // maybe J.Block
                .getParentOrThrow() // maybe J.ClassDecl
                .getTree() instanceof J.ClassDecl)) {
            return v;
        }

        JavaType.Primitive primitive = TypeUtils.asPrimitive(variable.getType());
        JavaType.Array array = TypeUtils.asArray(variable.getType());

        J tree = variableDeclsCursor.getTree();
        if(!(tree instanceof J.VariableDecls)) {
            return v;
        }

        J.VariableDecls variableDecls = (J.VariableDecls) tree;

        J.Literal literalInit = variable.getInitializer() instanceof J.Literal ? (J.Literal) variable.getInitializer() : null;

        if (literalInit != null && !variableDecls.hasModifier("final")) {
            if (TypeUtils.asClass(variable.getType()) != null && JavaType.Primitive.Null.equals(literalInit.getType())) {
                v = v.withInitializer(null).withName(stripSuffix(v.getName()));
            } else if (primitive != null && !onlyObjectReferences) {
                switch (primitive) {
                    case Boolean:
                        if (literalInit.getValue() == Boolean.valueOf(false)) {
                            v = v.withInitializer(null).withName(stripSuffix(v.getName()));
                        }
                        break;
                    case Char:
                        if (literalInit.getValue() != null && (Character) literalInit.getValue() == 0) {
                            v = v.withInitializer(null).withName(stripSuffix(v.getName()));
                        }
                        break;
                    case Int:
                    case Long:
                    case Short:
                        if (literalInit.getValue() != null && ((Number) literalInit.getValue()).intValue() == 0) {
                            v = v.withInitializer(null).withName(stripSuffix(v.getName()));
                        }
                        break;
                }
            } else if (array != null && JavaType.Primitive.Null.equals(literalInit.getType())) {
                v = v.withInitializer(null)
                        .withDimensionsAfterName(formatLastSuffix(v.getDimensionsAfterName(), ""));
            }
        }

        return v;
    }
}
