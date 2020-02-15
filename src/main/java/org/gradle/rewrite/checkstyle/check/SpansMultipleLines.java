package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.AstVisitor;

class SpansMultipleLines extends AstVisitor<Boolean> {
    private boolean visitedScope = false;

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

        return tree != null && tree.getFormatting().getPrefix().contains("\n") || super.visit(tree);
    }
}