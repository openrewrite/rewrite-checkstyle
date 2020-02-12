package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.ScopedRefactorVisitor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static java.util.stream.Collectors.toList;

public class EmptyStatement extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "EmptyStatement";
    }

    @Override
    public List<AstTransform> visitIf(Tr.If iff) {
        Tree parentBlock = getCursor().getParentOrThrow().getTree();
        Optional<Statement> nextStatement = nextStatement(iff);
        return maybeTransform(isEmptyStatement(iff.getThenPart()),
                super.visitIf(iff),
                transform(iff, i -> nextStatement
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(parentBlock.getId(), s));
                            return i.withThenPart(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(i);
                            return i;
                        }))
        );
    }

    @Override
    public List<AstTransform> visitForLoop(Tr.ForLoop forLoop) {
        Tree parentBlock = getCursor().getParentOrThrow().getTree();
        Optional<Statement> nextStatement = nextStatement(forLoop);
        return maybeTransform(isEmptyStatement(forLoop.getBody()),
                super.visitForLoop(forLoop),
                transform(forLoop, i -> nextStatement
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(parentBlock.getId(), s));
                            return i.withBody(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(i);
                            return i;
                        }))
        );
    }

    @Override
    public List<AstTransform> visitForEachLoop(Tr.ForEachLoop forEachLoop) {
        Tree parentBlock = getCursor().getParentOrThrow().getTree();
        Optional<Statement> nextStatement = nextStatement(forEachLoop);
        return maybeTransform(isEmptyStatement(forEachLoop.getBody()),
                super.visitForEachLoop(forEachLoop),
                transform(forEachLoop, i -> nextStatement
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(parentBlock.getId(), s));
                            return i.withBody(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(i);
                            return i;
                        }))
        );
    }

    @Override
    public List<AstTransform> visitWhileLoop(Tr.WhileLoop whileLoop) {
        Tree parentBlock = getCursor().getParentOrThrow().getTree();
        Optional<Statement> nextStatement = nextStatement(whileLoop);
        return maybeTransform(isEmptyStatement(whileLoop.getBody()),
                super.visitWhileLoop(whileLoop),
                transform(whileLoop, i -> nextStatement
                        .map(s -> {
                            andThen(new RemoveStatementFromParentBlock(parentBlock.getId(), s));
                            return i.withBody(s);
                        })
                        .orElseGet(() -> {
                            deleteStatement(i);
                            return i;
                        }))
        );
    }

    @SuppressWarnings("unchecked")
    private Optional<Statement> nextStatement(Statement statement) {
        Tree parent = getCursor().getParentOrThrow().getTree();
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

        public RemoveStatementFromParentBlock(UUID scope, Statement statement) {
            super(scope);
            this.statement = statement;
        }

        @Override
        public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
            return maybeTransform(block.getId().equals(scope),
                    super.visitBlock(block),
                    transform(block, b -> b.withStatements(b.getStatements().stream()
                        .filter(s -> s != statement)
                        .collect(toList()))));
        }
    }
}
