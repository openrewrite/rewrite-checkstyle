package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.refactor.Refactor;
import com.netflix.rewrite.tree.*;
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
public class EmptyBlock extends RefactorVisitor<Tree> {
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
    public List<AstTransform<Tree>> visitWhileLoop(Tr.WhileLoop whileLoop) {
        if (tokens.contains(LITERAL_WHILE) && isEmptyBlock(whileLoop.getBody())) {
            return transform(whileLoop.getBody(), b -> ((Tr.Block<Tree>) b).withStatements(
                    singletonList(new Tr.Continue(randomId(), null, INFER))
            ));
        }
        return super.visitWhileLoop(whileLoop);
    }

    @Override
    public List<AstTransform<Tree>> visitBlock(Tr.Block<Tree> block) {
        return Optional.ofNullable(getCursor().getParentOrThrow().getParent())
                .map(Cursor::getTree)
                .filter(t -> t instanceof Tr.ClassDecl && isEmptyBlock(block))
                .filter(t -> (tokens.contains(STATIC_INIT) && block.getStatic() != null) ||
                        (tokens.contains(INSTANCE_INIT) && block.getStatic() == null))
                .map(t -> transform(((Tr.ClassDecl) t).getBody(), body -> body
                        .withStatements(body.getStatements().stream()
                                .filter(s -> s != block)
                                .collect(toList()))))
                .orElse(super.visitBlock(block));
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform<Tree>> visitDoWhileLoop(Tr.DoWhileLoop doWhileLoop) {
        if (tokens.contains(LITERAL_DO) && isEmptyBlock(doWhileLoop.getBody())) {
            return transform(doWhileLoop.getBody(), b -> ((Tr.Block<Tree>) b).withStatements(
                    singletonList(new Tr.Continue(randomId(), null, INFER))
            ));
        }
        return super.visitDoWhileLoop(doWhileLoop);
    }

    @Override
    public List<AstTransform<Tree>> visitCatch(Tr.Catch catzh) {
        if (tokens.contains(LITERAL_CATCH) && isEmptyBlock(catzh.getBody())) {
            var exceptionType = catzh.getParam().getTree().getTypeExpr();
            var exceptionClassType = exceptionType == null ? null : TypeUtils.asClass(exceptionType.getType());

            final String throwName;
            final Type.Class throwClass;

            if (exceptionClassType != null && exceptionClassType.getFullyQualifiedName().equals("java.io.IOException")) {
                throwName = "UncheckedIOException";
                throwClass = Type.Class.build("java.io.UncheckedIOException");
            } else {
                throwName = "RuntimeException";
                throwClass = Type.Class.build("java.lang.RuntimeException");
            }

            return transform(catzh, c -> c.withBody(
                    catzh.getBody().withStatements(
                            singletonList(new Tr.Throw(randomId(),
                                    new Tr.NewClass(randomId(),
                                            Tr.Ident.build(randomId(), throwName, throwClass, format(" ")),
                                            new Tr.NewClass.Arguments(randomId(),
                                                    singletonList(Tr.Ident.build(randomId(), catzh.getParam().getTree().getVars().iterator().next().getSimpleName(),
                                                            exceptionType == null ? null : exceptionType.getType(), EMPTY)),
                                                    EMPTY),
                                            null,
                                            throwClass,
                                            format(" ")
                                    ),
                                    INFER))
                    )
            ));
        }

        return super.visitCatch(catzh);
    }

    @Override
    public List<AstTransform<Tree>> visitIf(Tr.If iff) {
        Tr.Block<Tree> containing = getCursor().getParentOrThrow().getTree();

        if (iff.getElsePart() == null) {
            return transform(containing, then -> {
                Tr.If iff2 = retrieve(iff, then);
                List<Tree> statements = new ArrayList<>(then.getStatements().size());
                for (Tree statement : then.getStatements()) {
                    if(statement == iff2) {
                        iff2.getIfCondition().getTree().getSideEffects()
                                .stream()
                                .map(s -> (Tree) s.withFormatting(INFER))
                                .forEach(statements::add);
                    }
                    else {
                        statements.add(statement);
                    }
                }
                return then.withStatements(statements);
            });
        }

        return super.visitIf(iff);
    }

    private boolean isEmptyBlock(Statement blockNode) {
        return block.equals(BlockPolicy.Statement) &&
                blockNode instanceof Tr.Block &&
                ((Tr.Block<?>) blockNode).getStatements().isEmpty();
    }

    public static Refactor run(Refactor refactor, EmptyBlock emptyBlock) {
        return refactor
                .run(emptyBlock)
                .addImport("java.io.UncheckedIOException", null, true);
    }
}
