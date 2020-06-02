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

import org.eclipse.microprofile.config.Config;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.refactor.DeleteStatement;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class EmptyStatement extends CheckstyleRefactorVisitor {
    public EmptyStatement() {
        super("checkstyle.EmptyStatement");
        setCursoringOn();
    }

    @AutoConfigure
    public static EmptyStatement configure(Config config) {
        return fromModule(config, "EmptyStatement", m -> new EmptyStatement());
    }

    @Override
    public J visitIf(J.If iff) {
        J.If i = refactor(iff, super::visitIf);
        return i.withThenPart(removeEmptyStatement(i.getThenPart()));
    }

    @Override
    public J visitForLoop(J.ForLoop forLoop) {
        J.ForLoop f = refactor(forLoop, super::visitForLoop);
        return f.withBody(removeEmptyStatement(f.getBody()));
    }

    @Override
    public J visitForEachLoop(J.ForEachLoop forEachLoop) {
        J.ForEachLoop f = refactor(forEachLoop, super::visitForEachLoop);
        return f.withBody(removeEmptyStatement(f.getBody()));
    }

    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop) {
        J.WhileLoop w = refactor(whileLoop, super::visitWhileLoop);
        return w.withBody(removeEmptyStatement(w.getBody()));
    }

    private Statement removeEmptyStatement(Statement t) {
        if (!isEmptyStatement(t)) {
            return t;
        }

        return nextStatement()
                .map(s -> {
                    // Remove the next statement's appearance in the parent block so it can be moved.
                    andThen(new RemoveStatementFromParentBlock(getCursor(), s));

                    // Move next statement in the parent block to be underneath this statement.
                    return s;
                })
                .orElseGet(() -> {
                    // This is the last statement in the block. There is nothing that could
                    // execute in the body of this statement, so just remove it.
                    andThen(new DeleteStatement(getCursor().getTree()));
                    return t;
                });
    }

    @SuppressWarnings("unchecked")
    private Optional<Statement> nextStatement() {
        Tree parent = getCursor().getParentOrThrow().getTree();
        return parent instanceof J.Block ?
                ((J.Block<Statement>) parent).getStatements().stream()
                        .dropWhile(s -> s != getCursor().getTree())
                        .skip(1)
                        .findFirst() :
                Optional.empty();
    }

    private boolean isEmptyStatement(Statement statement) {
        return statement instanceof J.Empty;
    }

    private static class RemoveStatementFromParentBlock extends JavaRefactorVisitor {
        private final Tree scope;
        private final Statement statement;

        public RemoveStatementFromParentBlock(Cursor cursor, Statement statement) {
            super("checkstyle.RemoveStatementFromParentBlock");
            this.scope = cursor.getParentOrThrow().getTree();
            this.statement = statement;
        }

        @Override
        public J visitBlock(J.Block<J> block) {
            J.Block<J> b = refactor(block, super::visitBlock);
            if (scope.isScope(block)) {
                b = b.withStatements(b.getStatements().stream()
                        .filter(s -> s != statement)
                        .collect(toList()));
            }
            return b;
        }
    }
}
