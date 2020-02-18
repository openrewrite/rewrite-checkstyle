package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class MultipleVariableDeclarations extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "MultipleVariableDeclarations";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitMultiVariable(Tr.VariableDecls multiVariable) {
        return maybeTransform(multiVariable,
                multiVariable.getVars().size() > 1 && getCursor().getParentOrThrow().getTree() instanceof Tr.Block,
                super::visitMultiVariable,
                mv -> (Tr.Block<Tree>) getCursor().getParentOrThrow().getTree(),
                block -> block.withStatements(block.getStatements().stream()
                        .flatMap(s -> {
                            if (s == multiVariable) {
                                Tr.VariableDecls mv = (Tr.VariableDecls) s;
                                return Stream.concat(
                                        Stream.of(mv.withVars(singletonList(mv.getVars().get(0)))),
                                        mv.getVars().stream().skip(1).map(var -> {
                                            List<Tr.VariableDecls.Dimension> dimensions = new ArrayList<>(mv.getDimensionsBeforeName());
                                            dimensions.addAll(var.getDimensionsAfterName());
                                            return new Tr.VariableDecls(randomId(),
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
