package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class HideUtilityClassConstructor extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.HideUtilityClassConstructor";
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
