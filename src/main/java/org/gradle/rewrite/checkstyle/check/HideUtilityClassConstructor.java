package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;

import java.util.List;

import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.stream.Collectors.toList;

public class HideUtilityClassConstructor extends RefactorVisitor {
    @Override
    public List<AstTransform> visitClassDecl(Tr.ClassDecl classDecl) {
        return maybeTransform(classDecl.getBody().getStatements().stream()
                        .allMatch(s -> !(s instanceof Tr.MethodDecl) ||
                                !((Tr.MethodDecl) s).isConstructor() ||
                                !((Tr.MethodDecl) s).hasModifier("static")),
                super.visitClassDecl(classDecl),
                transform(classDecl.getBody(), body -> body.withStatements(
                        body.getStatements().stream().map(s -> {
                            Tr.MethodDecl ctor = (Tr.MethodDecl) s;

                            if (ctor.isConstructor() && !ctor.hasModifier("private")) {
                                List<Tr.Modifier> modifiers = ctor.getModifiers();

                                int insertPosition = 0;
                                for (int i = 0; i < modifiers.size(); i++) {
                                    Tr.Modifier modifier = modifiers.get(i);
                                    if (modifier instanceof Tr.Modifier.Public) {
                                        insertPosition = i;
                                    }
                                }

                                modifiers.set(insertPosition, new Tr.Modifier.Private(randomId(), modifiers.get(insertPosition).getFormatting()));

                                return ctor.withModifiers(modifiers);
                            }

                            return ctor;
                        }).collect(toList()))
                )
        );
    }
}
