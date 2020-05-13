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
package org.openrewrite.checkstyle.check;

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
