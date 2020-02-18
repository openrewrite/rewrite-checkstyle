package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;

import java.util.List;

import static java.util.stream.Collectors.toList;

public class NoFinalizer extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "NoFinalizer";
    }

    @Override
    public List<AstTransform> visitMethod(Tr.MethodDecl method) {
        return maybeTransform(method,
                method.getSimpleName().equals("finalize") &&
                        method.getReturnTypeExpr() != null &&
                        Type.Primitive.Void.equals(method.getReturnTypeExpr().getType()) &&
                        method.getParams().getParams().stream().allMatch(p -> p instanceof Tr.Empty),
                super::visitMethod,
                m -> (Tr.Block<?>) getCursor().getParentOrThrow().getTree(),
                block -> block.withStatements(block.getStatements().stream()
                    .filter(s -> !s.getId().equals(method.getId()))
                    .collect(toList()))
        );
    }
}
