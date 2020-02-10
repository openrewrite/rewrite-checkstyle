package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.*;
import com.netflix.rewrite.tree.visitor.ReferencedTypesVisitor;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.BlockPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.*;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

/**
 * TODO offer option to log if a logger field is available instead of rethrowing as an unchecked exception.
 */
@Builder
public class EmptyBlock extends RefactorVisitor {
    @Builder.Default
    BlockPolicy block = BlockPolicy.Statement;

    @Builder.Default
    Set<Token> tokens = Set.of(
            LITERAL_WHILE,
            LITERAL_TRY,
            LITERAL_FINALLY,
            LITERAL_DO,
            LITERAL_IF,
            LITERAL_ELSE,
            LITERAL_FOR,
            INSTANCE_INIT,
            STATIC_INIT,
            LITERAL_SWITCH,
            LITERAL_SYNCHRONIZED
    );

    @Override
    protected String getRuleName() {
        return "EmptyBlock";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitWhileLoop(Tr.WhileLoop whileLoop) {
        List<AstTransform> changes = super.visitWhileLoop(whileLoop);
        if (tokens.contains(LITERAL_WHILE) && isEmptyBlock(whileLoop.getBody())) {
            changes.addAll(transform(whileLoop.getBody(), b -> ((Tr.Block<Tree>) b).withStatements(
                    singletonList(new Tr.Continue(randomId(), null, INFER))))
            );
        }
        return changes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitDoWhileLoop(Tr.DoWhileLoop doWhileLoop) {
        List<AstTransform> changes = super.visitDoWhileLoop(doWhileLoop);
        if (tokens.contains(LITERAL_DO) && isEmptyBlock(doWhileLoop.getBody())) {
            changes.addAll(transform(doWhileLoop.getBody(), b -> ((Tr.Block<Tree>) b).withStatements(
                    singletonList(new Tr.Continue(randomId(), null, INFER))))
            );
        }
        return changes;
    }

    @Override
    public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
        return Optional.ofNullable(getCursor().getParentOrThrow().getParent())
                .map(Cursor::getTree)
                .filter(t -> t instanceof Tr.ClassDecl && isEmptyBlock(block))
                .filter(t -> (tokens.contains(STATIC_INIT) && block.getStatic() != null) ||
                        (tokens.contains(INSTANCE_INIT) && block.getStatic() == null))
                .map(t -> maybeTransform(true, super.visitBlock(block),
                        transform(((Tr.ClassDecl) t).getBody(), body -> body
                            .withStatements(body.getStatements().stream()
                                    .filter(s -> s != block)
                                    .collect(toList())))))
                .orElse(super.visitBlock(block));
    }

    @Override
    public List<AstTransform> visitCatch(Tr.Catch catzh) {
        return maybeTransform(tokens.contains(LITERAL_CATCH) && isEmptyBlock(catzh.getBody()),
                super.visitCatch(catzh),
                transform(catzh, c -> {
                    var exceptionType = c.getParam().getTree().getTypeExpr();
                    var exceptionClassType = exceptionType == null ? null : TypeUtils.asClass(exceptionType.getType());

                    final String throwName;
                    final Type.Class throwClass;

                    if (exceptionClassType != null && exceptionClassType.getFullyQualifiedName().equals("java.io.IOException")) {
                        throwName = "UncheckedIOException";
                        throwClass = Type.Class.build("java.io.UncheckedIOException");
                        maybeAddImport(throwClass);
                    } else {
                        throwName = "RuntimeException";
                        throwClass = Type.Class.build("java.lang.RuntimeException");
                    }

                    return c.withBody(
                            c.getBody().withStatements(
                                    singletonList(new Tr.Throw(randomId(),
                                            new Tr.NewClass(randomId(),
                                                    Tr.Ident.build(randomId(), throwName, throwClass, format(" ")),
                                                    new Tr.NewClass.Arguments(randomId(),
                                                            singletonList(Tr.Ident.build(randomId(), c.getParam().getTree().getVars().iterator().next().getSimpleName(),
                                                                    exceptionType == null ? null : exceptionType.getType(), EMPTY)),
                                                            EMPTY),
                                                    null,
                                                    throwClass,
                                                    format(" ")
                                            ),
                                            INFER))
                            )
                    );
                }));
    }

    @Override
    public List<AstTransform> visitTry(Tr.Try tryable) {
        List<AstTransform> changes = super.visitTry(tryable);

        if (tokens.contains(LITERAL_TRY) && isEmptyBlock(tryable.getBody())) {
            deleteStatement(tryable);
        } else if (tokens.contains(LITERAL_FINALLY) && tryable.getFinally() != null && isEmptyBlock(tryable.getFinally().getBlock())) {
            changes.addAll(transform(tryable, t -> t.withFinallie(null)));
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitIf(Tr.If iff) {
        List<AstTransform> changes = super.visitIf(iff);
        Tr.Block<Tree> containing = getCursor().getParentOrThrow().getTree();

        if (iff.getElsePart() == null) {
            changes.addAll(transform(containing, then -> {
                        Tr.If iff2 = retrieve(iff, then);
                        List<Tree> statements = new ArrayList<>(then.getStatements().size());
                        for (Tree statement : then.getStatements()) {
                            if (statement == iff2) {
                                iff2.getIfCondition().getTree().getSideEffects()
                                        .stream()
                                        .map(s -> (Tree) s.withFormatting(INFER))
                                        .forEach(statements::add);
                            } else {
                                statements.add(statement);
                            }
                        }
                        return then.withStatements(statements);
                    })
            );
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitSynchronized(Tr.Synchronized synch) {
        if (isEmptyBlock(synch)) {
            deleteStatement(synch);
        }

        return super.visitSynchronized(synch);
    }

    private boolean isEmptyBlock(Statement blockNode) {
        return block.equals(BlockPolicy.Statement) &&
                blockNode instanceof Tr.Block &&
                ((Tr.Block<?>) blockNode).getStatements().isEmpty();
    }
}
