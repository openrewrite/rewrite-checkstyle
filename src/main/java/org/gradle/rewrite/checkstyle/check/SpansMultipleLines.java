package org.gradle.rewrite.checkstyle.check;

import lombok.Getter;
import org.openrewrite.ScopedVisitorSupport;
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaSourceVisitor;
import org.openrewrite.java.tree.J;

import java.util.UUID;

class SpansMultipleLines extends JavaSourceVisitor<Boolean> implements ScopedVisitorSupport {
    @Getter
    private final UUID scope;

    @Nullable
    private final J skip;

    SpansMultipleLines(J scope, J skip) {
        this.scope = scope.getId();
        this.skip = skip;
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public Boolean defaultTo(Tree t) {
        return false;
    }

    @Override
    public Boolean visitTree(Tree tree) {
        if (isScope()) {
            // don't look at the prefix of the scope that we are testing, we are interested in its contents
            return super.visitTree(tree);
        } else if (isScopeInCursorPath()) {
            if (tree instanceof J.Block && ((J.Block<?>) tree).getEndOfBlockSuffix().contains("\n")) {
                return true;
            }

            if (tree == skip) {
                return false;
            }

            return tree != null && tree.getFormatting().getPrefix().contains("\n") || super.visitTree(tree);
        } else {
            return false;
        }
    }
}
