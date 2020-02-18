package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.ScopedRefactorVisitor;

import java.util.List;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

public class EmptyStatement extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "EmptyStatement";
    }

    @Override
    public List<AstTransform> visitIf(Tr.If iff) {
        return maybeTransform(iff,
                isEmptyStatement(iff.getThenPart()),
                super::visitIf,
                (i, cursor) -> nextStatement(i, cursor)
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(cursor, s));
                            return i.withThenPart(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(i);
                            return i;
                        })
        );
    }

    @Override
    public List<AstTransform> visitForLoop(Tr.ForLoop forLoop) {
        return maybeTransform(forLoop,
                isEmptyStatement(forLoop.getBody()),
                super::visitForLoop,
                (f, cursor) -> nextStatement(f, cursor)
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(cursor, s));
                            return f.withBody(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(f);
                            return f;
                        })
        );
    }

    @Override
    public List<AstTransform> visitForEachLoop(Tr.ForEachLoop forEachLoop) {
        return maybeTransform(forEachLoop,
                isEmptyStatement(forEachLoop.getBody()),
                super::visitForEachLoop,
                (f, cursor) -> nextStatement(f, cursor)
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(cursor, s));
                            return f.withBody(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(f);
                            return f;
                        })
        );
    }

    @Override
    public List<AstTransform> visitWhileLoop(Tr.WhileLoop whileLoop) {
        return maybeTransform(whileLoop,
                isEmptyStatement(whileLoop.getBody()),
                super::visitWhileLoop,
                (w, cursor) -> nextStatement(w, cursor)
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(cursor, s));
                            return w.withBody(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(w);
                            return w;
                        })
        );
    }

    @SuppressWarnings("unchecked")
    private Optional<Statement> nextStatement(Statement statement, Cursor cursor) {
        Tree parent = cursor.getParentOrThrow().getTree();
        return parent instanceof Tr.Block ?
                ((Tr.Block<Statement>) parent).getStatements().stream()
                        .dropWhile(s -> s != statement)
                        .skip(1)
                        .findFirst() :
                Optional.empty();
    }

    private boolean isEmptyStatement(Statement statement) {
        return statement instanceof Tr.Empty;
    }

    private static class RemoveStatementFromParentBlock extends ScopedRefactorVisitor {
        private final Statement statement;

        public RemoveStatementFromParentBlock(Cursor cursor, Statement statement) {
            super(cursor.getParentOrThrow().getTree().getId());
            this.statement = statement;
        }

        @Override
        public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
            return maybeTransform(block,
                    block.getId().equals(scope),
                    super::visitBlock,
                    b -> b.withStatements(b.getStatements().stream()
                            .filter(s -> s != statement)
                            .collect(toList())));
        }
    }
}
