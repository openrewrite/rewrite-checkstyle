package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.AstVisitor;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.ScopedRefactorVisitor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.netflix.rewrite.tree.Tr.randomId;

@Builder
public class FallThrough extends RefactorVisitor {
    @Builder.Default
    private final boolean checkLastCaseGroup = false;

    @Builder.Default
    private final Pattern reliefPattern = Pattern.compile("falls?[ -]?thr(u|ough)");

    @Override
    public String getRuleName() {
        return "FallThrough";
    }

    @Override
    public List<AstTransform> visitCase(Tr.Case caze) {
        Tr.Switch switzh = getCursor().getParentOrThrow().getParentOrThrow().getTree();
        if ((checkLastCaseGroup || !isLastCase(caze)) && !new LastLineBreaksOrFallsThrough(caze).visit(switzh)) {
            andThen(new AddBreak(caze.getId()));
        }
        return super.visitCase(caze);
    }

    private boolean isLastCase(Tr.Case caze) {
        Tr.Block<Statement> switchBlock = getCursor().getParentOrThrow().getTree();
        return caze == switchBlock.getStatements().get(switchBlock.getStatements().size() - 1);
    }

    private static class AddBreak extends ScopedRefactorVisitor {
        public AddBreak(UUID scope) {
            super(scope);
        }

        @Override
        public List<AstTransform> visitCase(Tr.Case caze) {
            return maybeTransform(caze,
                    caze.getId().equals(scope) && caze.getStatements().stream()
                            .reduce((s1, s2) -> s2)
                            .map(s -> !(s instanceof Tr.Block))
                            .orElse(true),
                    super::visitCase,
                    (c, cursor) -> {
                        List<Statement> statements = caze.getStatements();
                        Tr.Block<Tr.Case> switchBlock = cursor.getParentOrThrow().getTree();
                        statements.add(new Tr.Break(randomId(), null, formatter().format(switchBlock)));
                        return c.withStatements(statements);
                    }
            );
        }

        @Override
        public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
            return maybeTransform(block,
                    isInScope(block) && block.getStatements().stream()
                            .reduce((s1, s2) -> s2)
                            .map(s -> !(s instanceof Tr.Block))
                            .orElse(true),
                    super::visitBlock,
                    b -> {
                        List<Tree> statements = b.getStatements();
                        statements.add(new Tr.Break(randomId(), null, formatter().format(b)));
                        return b.withStatements(statements);
                    }
            );
        }
    }

    @RequiredArgsConstructor
    private class LastLineBreaksOrFallsThrough extends AstVisitor<Boolean> {
        private final Tr.Case scope;

        @Override
        public Boolean defaultTo(Tree t) {
            return false;
        }

        @Override
        public Boolean visitSwitch(Tr.Switch switzh) {
            List<Tr.Case> statements = switzh.getCases().getStatements();
            for (int i = 0; i < statements.size() - 1; i++) {
                Tr.Case caze = statements.get(i);
                // because a last-line comment winds up getting attached as a formatting prefix to the NEXT case statement!
                if (caze == scope && reliefPattern.matcher(statements.get(i + 1).getFormatting().getPrefix()).find()) {
                    return true;
                }
            }
            return super.visitSwitch(switzh);
        }

        @Override
        public Boolean visitCase(Tr.Case caze) {
            return caze == scope && (lastLineBreaksOrFallsThrough(caze.getStatements()) || super.visitCase(caze));
        }

        @Override
        public Boolean visitBlock(Tr.Block<Tree> block) {
            return lastLineBreaksOrFallsThrough(block.getStatements()) ||
                    reliefPattern.matcher(block.getEndOfBlockSuffix()).find() ||
                    super.visitBlock(block);
        }

        private boolean lastLineBreaksOrFallsThrough(List<? extends Tree> trees) {
            return trees.stream()
                    .reduce((s1, s2) -> s2) // last statement
                    .map(s -> s instanceof Tr.Return ||
                            s instanceof Tr.Break ||
                            s instanceof Tr.Continue ||
                            (s instanceof Tr.Empty && reliefPattern.matcher(s.getFormatting().getPrefix()).find()) ||
                            reliefPattern.matcher(s.getFormatting().getSuffix()).find())
                    .orElse(false);
        }
    }
}
