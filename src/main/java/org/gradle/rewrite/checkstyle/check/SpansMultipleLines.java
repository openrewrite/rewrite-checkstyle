package org.gradle.rewrite.checkstyle.check;

import lombok.Getter;
import org.openrewrite.ScopedVisitorSupport;
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaSourceVisitor;
import org.openrewrite.java.tree.J;

import java.util.Spliterators;
import java.util.UUID;

import static java.util.stream.StreamSupport.stream;

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
            if (tree instanceof J.Block && ((J.Block<?>) tree).getEndOfBlockSuffix().contains("\n")) {
                return true;
            }

            // don't look at the prefix of the scope that we are testing, we are interested in its contents
            return super.visitTree(tree);
        } else if (isScopeInCursorPath() && !isSkipInCursorPath()) {
            if (tree instanceof J.Block && ((J.Block<?>) tree).getEndOfBlockSuffix().contains("\n")) {
                return true;
            }

            return tree != null && tree.getFormatting().getPrefix().contains("\n") || super.visitTree(tree);
        } else {
            return false;
        }
    }

    private boolean isSkipInCursorPath() {
        Tree t = getCursor().getTree();
        return skip != null && ((t != null && t.getId().equals(skip.getId())) ||
                stream(Spliterators.spliteratorUnknownSize(getCursor().getPath(), 0), false)
                        .anyMatch(p -> p.getId().equals(skip.getId())));
    }
}
