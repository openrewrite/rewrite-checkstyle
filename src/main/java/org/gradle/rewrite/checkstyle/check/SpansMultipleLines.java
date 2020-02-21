package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.internal.lang.Nullable;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.visitor.AstVisitor;
import lombok.RequiredArgsConstructor;

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

            if(tree instanceof Tr.Block && ((Tr.Block<?>) tree).getEndOfBlockSuffix().contains("\n")) {
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