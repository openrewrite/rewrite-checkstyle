package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static com.netflix.rewrite.tree.Formatting.format;
import static com.netflix.rewrite.tree.Formatting.formatFirstPrefix;
import static com.netflix.rewrite.tree.Tr.randomId;

public class FinalClass extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.FinalClass";
    }

    @Override
    public List<AstTransform> visitClassDecl(Tr.ClassDecl classDecl) {
        return maybeTransform(classDecl,
                classDecl.getBody().getStatements().stream()
                        .noneMatch(s -> s instanceof Tr.MethodDecl &&
                                ((Tr.MethodDecl) s).isConstructor() &&
                                !((Tr.MethodDecl) s).hasModifier("private")),
                super::visitClassDecl,
                cd -> {
                    List<Tr.Modifier> modifiers = cd.getModifiers();

                    int insertPosition = 0;
                    for (int i = 0; i < modifiers.size(); i++) {
                        Tr.Modifier modifier = modifiers.get(i);
                        if (modifier instanceof Tr.Modifier.Public || modifier instanceof Tr.Modifier.Static) {
                            insertPosition = i + 1;
                        }
                    }

                    Formatting format = format(" ");
                    if (insertPosition == 0 && !modifiers.isEmpty()) {
                        format = modifiers.get(0).getFormatting();
                        formatFirstPrefix(modifiers, " ");
                    }

                    modifiers.add(insertPosition, new Tr.Modifier.Final(randomId(), format));

                    return cd.withModifiers(modifiers);
                }
        );
    }
}
