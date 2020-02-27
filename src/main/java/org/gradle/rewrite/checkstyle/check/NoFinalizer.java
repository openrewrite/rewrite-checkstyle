package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.J;
import org.openrewrite.tree.Type;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class NoFinalizer extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.NoFinalizer";
    }

    @Override
    public List<AstTransform> visitMethod(J.MethodDecl method) {
        return maybeTransform(method,
                method.getSimpleName().equals("finalize") &&
                        method.getReturnTypeExpr() != null &&
                        Type.Primitive.Void.equals(method.getReturnTypeExpr().getType()) &&
                        method.getParams().getParams().stream().allMatch(p -> p instanceof J.Empty),
                super::visitMethod,
                m -> (J.Block<?>) getCursor().getParentOrThrow().getTree(),
                block -> block.withStatements(block.getStatements().stream()
                    .filter(s -> !s.getId().equals(method.getId()))
                    .collect(toList()))
        );
    }
}
