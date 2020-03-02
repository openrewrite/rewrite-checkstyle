package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.refactor.DeleteStatement;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.refactor.ScopedJavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class EmptyStatement extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.EmptyStatement";
    }

    @Override
    public boolean isCursored() {
        return true;
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

    private static class RemoveStatementFromParentBlock extends ScopedJavaRefactorVisitor {
        private final Statement statement;

        public RemoveStatementFromParentBlock(Cursor cursor, Statement statement) {
            super(cursor.getParentOrThrow().getTree().getId());
            this.statement = statement;
        }

        @Override
        public J visitBlock(J.Block<J> block) {
            J.Block<J> b = refactor(block, super::visitBlock);
            if (isScope()) {
                b = b.withStatements(b.getStatements().stream()
                        .filter(s -> s != statement)
                        .collect(toList()));
            }
            return b;
        }
    }
}
