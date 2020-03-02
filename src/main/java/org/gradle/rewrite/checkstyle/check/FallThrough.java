package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.openrewrite.Tree;
import org.openrewrite.java.JavaSourceVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;
import org.openrewrite.java.visitor.refactor.ScopedJavaRefactorVisitor;

import java.util.*;
import java.util.regex.Pattern;

import static org.openrewrite.Tree.randomId;

@Builder
public class FallThrough extends JavaRefactorVisitor {
    @Builder.Default
    private final boolean checkLastCaseGroup = false;

    @Builder.Default
    private final Pattern reliefPattern = Pattern.compile("falls?[ -]?thr(u|ough)");

    private final Set<UUID> casesToAddBreak = new HashSet<>();

    @Override
    public String getName() {
        return "checkstyle.FallThrough";
    }

    @Override
    public boolean isCursored() {
        return true;
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
                andThen(new AddBreak(caze.getId()));
            }
        }
        return super.visitCase(caze);
    }

    private boolean isLastCase(J.Case caze) {
        J.Block<Statement> switchBlock = getCursor().getParentOrThrow().getTree();
        return caze == switchBlock.getStatements().get(switchBlock.getStatements().size() - 1);
    }

    private static class AddBreak extends ScopedJavaRefactorVisitor {
        public AddBreak(UUID scope) {
            super(scope);
        }

        @Override
        public J visitCase(J.Case caze) {
            J.Case c = refactor(caze, super::visitCase);

            if (isScope() &&
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

            if (isScopeInCursorPath() &&
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

    @RequiredArgsConstructor
    private class LastLineBreaksOrFallsThrough extends JavaSourceVisitor<Boolean> {
        private final J.Case scope;

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
