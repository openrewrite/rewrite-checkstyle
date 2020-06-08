/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.checkstyle;

import org.openrewrite.AutoConfigure;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class MultipleVariableDeclarations extends CheckstyleRefactorVisitor {
    public MultipleVariableDeclarations() {
        setCursoringOn();
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
