package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;

import static java.util.stream.Collectors.toList;

public class NoFinalizer extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.NoFinalizer";
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        J.ClassDecl c = refactor(classDecl, super::visitClassDecl);

        return classDecl.getMethods().stream()
                .filter(method -> method.getSimpleName().equals("finalize") &&
                        method.getReturnTypeExpr() != null &&
                        JavaType.Primitive.Void.equals(method.getReturnTypeExpr().getType()) &&
                        method.getParams().getParams().stream().allMatch(p -> p instanceof J.Empty))
                .findAny()
                .map(m -> c.withBody(c.getBody().withStatements(c.getBody().getStatements().stream()
                        .filter(s -> !s.getId().equals(m.getId()))
                        .collect(toList())))
                )
                .orElse(c);
    }
}
