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

import org.openrewrite.Tree;
import org.openrewrite.AutoConfigure;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.JavaSourceVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.*;
import java.util.regex.Pattern;

import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class FallThrough extends CheckstyleRefactorVisitor {
    private boolean checkLastCaseGroup;
    private Pattern reliefPattern;

    private final Set<UUID> casesToAddBreak = new HashSet<>();

    public FallThrough() {
        setCursoringOn();
    }

    @Override
    protected void configure(Module m) {
        this.checkLastCaseGroup = m.prop("checkLastCaseGroup", false);
        this.reliefPattern = m.prop("reliefPattern", Pattern.compile("falls?[ -]?thr(u|ough)"));
    }

    @Override
    public void nextCycle() {
        casesToAddBreak.clear();
        super.nextCycle();
    }

    @Override
    public J visitCase(J.Case caze) {
        J.Switch switzh = getCursor().getParentOrThrow().getParentOrThrow().getTree();
        if ((checkLastCaseGroup || !isLastCase(caze)) && !new LastLineBreaksOrFallsThrough(caze).visit(switzh)) {
            if (casesToAddBreak.add(caze.getId())) {
                andThen(new AddBreak(caze));
            }
        }
        return super.visitCase(caze);
    }

    private boolean isLastCase(J.Case caze) {
        J.Block<Statement> switchBlock = getCursor().getParentOrThrow().getTree();
        return caze == switchBlock.getStatements().get(switchBlock.getStatements().size() - 1);
    }

    private static class AddBreak extends JavaRefactorVisitor {
        private final J.Case scope;

        public AddBreak(J.Case scope) {
            this.scope = scope;
            setCursoringOn();
        }

        @Override
        public J visitCase(J.Case caze) {
            J.Case c = refactor(caze, super::visitCase);

            if (scope.isScope(caze) &&
                    c.getStatements().stream().noneMatch(s -> s instanceof J.Break) &&
                    c.getStatements().stream()
                            .reduce((s1, s2) -> s2)
                            .map(s -> !(s instanceof J.Block))
                            .orElse(true)) {
                List<Statement> statements = new ArrayList<>(c.getStatements());
                J.Block<J.Case> switchBlock = getCursor().getParentOrThrow().getTree();
                statements.add(new J.Break(randomId(), null, formatter.format(switchBlock)));
                c = c.withStatements(statements);
            }

            return c;
        }

        @Override
        public J visitBlock(J.Block<J> block) {
            J.Block<J> b = refactor(block, super::visitBlock);

            if (getCursor().isScopeInPath(scope) &&
                    block.getStatements().stream().noneMatch(s -> s instanceof J.Break) &&
                    block.getStatements().stream()
                            .reduce((s1, s2) -> s2)
                            .map(s -> !(s instanceof J.Block))
                            .orElse(true)) {
                List<J> statements = b.getStatements();
                statements.add(new J.Break(randomId(), null, formatter.format(b)));
                b = b.withStatements(statements);
            }

            return b;
        }
    }

    private class LastLineBreaksOrFallsThrough extends JavaSourceVisitor<Boolean> {
        private final J.Case scope;

        private LastLineBreaksOrFallsThrough(J.Case scope) {
            this.scope = scope;
        }

        @Override
        public Boolean defaultTo(Tree t) {
            return false;
        }

        @Override
        public Boolean visitSwitch(J.Switch switzh) {
            List<J.Case> statements = switzh.getCases().getStatements();

            for (int i = 0; i < statements.size() - 1; i++) {
                J.Case caze = statements.get(i);
                // because a last-line comment winds up getting attached as a formatting prefix to the NEXT case statement!
                if (caze == scope && reliefPattern.matcher(statements.get(i + 1).getFormatting().getPrefix()).find()) {
                    return true;
                }
            }

            return super.visitSwitch(switzh);
        }

        @Override
        public Boolean visitCase(J.Case caze) {
            return caze == scope && (lastLineBreaksOrFallsThrough(caze.getStatements()) || super.visitCase(caze));
        }

        @Override
        public Boolean visitBlock(J.Block<J> block) {
            return lastLineBreaksOrFallsThrough(block.getStatements()) ||
                    reliefPattern.matcher(block.getEndOfBlockSuffix()).find() ||
                    super.visitBlock(block);
        }

        private boolean lastLineBreaksOrFallsThrough(List<? extends Tree> trees) {
            return trees.stream()
                    .reduce((s1, s2) -> s2) // last statement
                    .map(s -> s instanceof J.Return ||
                            s instanceof J.Break ||
                            s instanceof J.Continue ||
                            (s instanceof J.Empty && reliefPattern.matcher(s.getFormatting().getPrefix()).find()) ||
                            reliefPattern.matcher(s.getFormatting().getSuffix()).find())
                    .orElse(false);
        }
    }
}
