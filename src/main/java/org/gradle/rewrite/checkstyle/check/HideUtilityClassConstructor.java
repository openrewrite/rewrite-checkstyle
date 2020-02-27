package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.J;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.tree.J.randomId;

public class HideUtilityClassConstructor extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.HideUtilityClassConstructor";
    }

    @Override
    public List<AstTransform> visitClassDecl(J.ClassDecl classDecl) {
        return maybeTransform(classDecl,
                classDecl.getBody().getStatements().stream()
                        .allMatch(s -> !(s instanceof J.MethodDecl) ||
                                !((J.MethodDecl) s).isConstructor() ||
                                !((J.MethodDecl) s).hasModifier("static")),
                super::visitClassDecl,
                J.ClassDecl::getBody,
                body -> body.withStatements(body.getStatements().stream().map(s -> {
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
                }).collect(toList()))
        );
    }
}
