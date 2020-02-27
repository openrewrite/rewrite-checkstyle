package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.Formatting;
import org.openrewrite.tree.J;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static org.openrewrite.tree.Formatting.format;
import static org.openrewrite.tree.Formatting.formatFirstPrefix;
import static org.openrewrite.tree.J.randomId;

public class FinalClass extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.FinalClass";
    }

    @Override
    public boolean isSingleRun() {
        return true;
    }

    @Override
    public List<AstTransform> visitClassDecl(J.ClassDecl classDecl) {
        return maybeTransform(classDecl,
                classDecl.getBody().getStatements().stream()
                        .noneMatch(s -> s instanceof J.MethodDecl &&
                                ((J.MethodDecl) s).isConstructor() &&
                                !((J.MethodDecl) s).hasModifier("private")),
                super::visitClassDecl,
                cd -> {
                    List<J.Modifier> modifiers = cd.getModifiers();

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

                    return cd.withModifiers(modifiers);
                }
        );
    }
}
