package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.Cursor;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Statement;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.ScopedRefactorVisitor;

import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

import static java.util.stream.Collectors.toList;

public class EmptyStatement extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.EmptyStatement";
    }

    @Override
    public List<AstTransform> visitIf(J.If iff) {
        return maybeTransform(iff,
                isEmptyStatement(iff.getThenPart()),
                super::visitIf,
                removeNextStatement(J.If::withThenPart));
    }

    @Override
    public List<AstTransform> visitForLoop(J.ForLoop forLoop) {
        return maybeTransform(forLoop,
                isEmptyStatement(forLoop.getBody()),
                super::visitForLoop,
                removeNextStatement(J.ForLoop::withBody));
    }

    @Override
    public List<AstTransform> visitForEachLoop(J.ForEachLoop forEachLoop) {
        return maybeTransform(forEachLoop,
                isEmptyStatement(forEachLoop.getBody()),
                super::visitForEachLoop,
                removeNextStatement(J.ForEachLoop::withBody));
    }

    @Override
    public List<AstTransform> visitWhileLoop(J.WhileLoop whileLoop) {
        return maybeTransform(whileLoop,
                isEmptyStatement(whileLoop.getBody()),
                super::visitWhileLoop,
                removeNextStatement(J.WhileLoop::withBody));
    }

    private <T extends Statement> BiFunction<T, Cursor, T> removeNextStatement(BiFunction<T, Statement, T> withStatement) {
        return (T t, Cursor cursor) -> nextStatement(t, cursor)
                .map(s -> {
                    andThen(new RemoveStatementFromParentBlock(cursor, s));
                    return withStatement.apply(t, s);
                })
                .orElseGet(() -> {
                    deleteStatement(t);
                    return t;
                });
    }

    @SuppressWarnings("unchecked")
    private Optional<Statement> nextStatement(Statement statement, Cursor cursor) {
        Tree parent = cursor.getParentOrThrow().getTree();
        return parent instanceof J.Block ?
                ((J.Block<Statement>) parent).getStatements().stream()
                        .dropWhile(s -> s != statement)
                        .skip(1)
                        .findFirst() :
                Optional.empty();
    }

    private boolean isEmptyStatement(Statement statement) {
        return statement instanceof J.Empty;
    }

    private static class RemoveStatementFromParentBlock extends ScopedRefactorVisitor {
        private final Statement statement;

        public RemoveStatementFromParentBlock(Cursor cursor, Statement statement) {
            super(cursor.getParentOrThrow().getTree().getId());
            this.statement = statement;
        }

        @Override
        public List<AstTransform> visitBlock(J.Block<Tree> block) {
            return maybeTransform(block,
                    block.getId().equals(scope),
                    super::visitBlock,
                    b -> b.withStatements(b.getStatements().stream()
                            .filter(s -> s != statement)
                            .collect(toList())));
        }
    }
}
