package org.gradle.rewrite.checkstyle.check;

import lombok.RequiredArgsConstructor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.AstVisitor;

@RequiredArgsConstructor
class SpansMultipleLines extends AstVisitor<Boolean> {
    private boolean visitedScope = false;

    @Nullable
    private final Tree skip;

    @Override
    public Boolean defaultTo(Tree t) {
        return false;
    }

    @Override
    public Boolean visit(Tree tree) {
        if(!visitedScope) {
            visitedScope = true;

            if(tree instanceof J.Block && ((J.Block<?>) tree).getEndOfBlockSuffix().contains("\n")) {
                return true;
            }
            // don't look at the prefix of the scope that we are testing, we are interested in its contents
            return super.visit(tree);
        }

        if(tree == skip) {
            return false;
        }

        return tree != null && tree.getFormatting().getPrefix().contains("\n") || super.visit(tree);
    }
}