package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.tree.J.randomId;

public class MultipleVariableDeclarations extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.MultipleVariableDeclarations";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitMultiVariable(J.VariableDecls multiVariable) {
        return maybeTransform(multiVariable,
                multiVariable.getVars().size() > 1 && getCursor().getParentOrThrow().getTree() instanceof J.Block,
                super::visitMultiVariable,
                mv -> (J.Block<Tree>) getCursor().getParentOrThrow().getTree(),
                block -> block.withStatements(block.getStatements().stream()
                        .flatMap(s -> {
                            if (s == multiVariable) {
                                J.VariableDecls mv = (J.VariableDecls) s;
                                return Stream.concat(
                                        Stream.of(mv.withVars(singletonList(mv.getVars().get(0)))),
                                        mv.getVars().stream().skip(1).map(var -> {
                                            List<J.VariableDecls.Dimension> dimensions = new ArrayList<>(mv.getDimensionsBeforeName());
                                            dimensions.addAll(var.getDimensionsAfterName());
                                            return new J.VariableDecls(randomId(),
                                                    mv.getAnnotations(),
                                                    mv.getModifiers(),
                                                    mv.getTypeExpr(),
                                                    null,
                                                    emptyList(),
                                                    singletonList(var.withDimensionsAfterName(dimensions)),
                                                    formatter().format(block));
                                        })
                                );
                            }
                            return Stream.of(s);
                        })
                        .collect(toList()))
        );
    }
}
