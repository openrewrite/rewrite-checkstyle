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
import org.openrewrite.Tree;
import org.openrewrite.java.search.FindReferencesToVariable;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

public class FinalLocalVariable extends CheckstyleRefactorVisitor {
    public FinalLocalVariable() {
        super("checkstyle.FinalLocalVariable");
        setCursoringOn();
    }

    @AutoConfigure
    public static FinalLocalVariable configure(Config config) {
        return fromModule(config, "FinalLocalVariable", m -> new FinalLocalVariable());
    }

    @Override
    public J visitMultiVariable(J.VariableDecls multiVariable) {

        J.VariableDecls mv = refactor(multiVariable, super::visitMultiVariable);

        Tree variableScope = getCursor().getParentOrThrow().getParentOrThrow().getTree();
        if (variableScope instanceof J.ClassDecl) {
            // we don't care about fields here
            return mv;
        }

        if (!multiVariable.hasModifier("final") && multiVariable.getVars().stream()
                .anyMatch(variable -> new FindReferencesToVariable(variable.getName()).visit(variableScope).size() +
                        (variable.getInitializer() == null ? -1 : 0) <= 0)) {
            List<J.Modifier> modifiers = new ArrayList<>();
            modifiers.add(new J.Modifier.Final(randomId(), mv.getTypeExpr() == null ? EMPTY :
                    format(mv.getTypeExpr().getFormatting().getPrefix())));
            modifiers.addAll(formatFirstPrefix(mv.getModifiers(), " "));

            mv = mv.withModifiers(modifiers).withTypeExpr(mv.getTypeExpr().withPrefix(" "));
        }

        return mv;
    }
}
