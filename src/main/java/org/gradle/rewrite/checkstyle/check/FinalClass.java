package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Formatting;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;

import java.util.List;

import static org.openrewrite.Formatting.format;
import static org.openrewrite.Formatting.formatFirstPrefix;
import static org.openrewrite.Tree.randomId;

public class FinalClass extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.FinalClass";
    }

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
