package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

public class MultipleVariableDeclarations extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.MultipleVariableDeclarations";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitBlock(J.Block<J> block) {
        J.Block<J> b = refactor(block, super::visitBlock);

        AtomicBoolean splitAtLeastOneVariable = new AtomicBoolean(false);

        List<J> statements = block.getStatements().stream()
                .flatMap(s -> s.whenType(J.VariableDecls.class)
                        .map(multiVariable -> {
                            if (multiVariable.getVars().size() > 1 && getCursor().getTree() instanceof J.Block) {
                                splitAtLeastOneVariable.set(true);
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
                                                    formatter.format(block));
                                        })
                                );
                            }
                            return Stream.of(s);
                        })
                        .orElse(Stream.of(s))
                )
                .collect(toList());

        return splitAtLeastOneVariable.get() ? b.withStatements(statements) : b;
    }
}
