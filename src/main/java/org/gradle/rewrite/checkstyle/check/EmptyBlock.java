package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.TypeTree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.BlockPolicy;
import org.gradle.rewrite.checkstyle.policy.LiteralToken;

import java.util.List;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.*;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.singletonList;
import static org.gradle.rewrite.checkstyle.policy.LiteralToken.*;

/**
 * TODO offer option to log if a logger field is available instead of rethrowing as an unchecked exception.
 */
@Builder
public class EmptyBlock extends RefactorVisitor<Tree> {
    @Builder.Default
    BlockPolicy block = BlockPolicy.Statement;

    @Builder.Default
    Set<LiteralToken> tokens = Set.of(
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

    @Override
    public List<AstTransform<Tree>> visitCatch(Tr.Catch catzh) {
        if (tokens.contains(LITERAL_CATCH) && isEmpty(catzh.getBody())) {
            return transform(catzh, c -> {
                TypeTree exceptionType = catzh.getParam().getTree().getTypeExpr();
                return ((Tr.Catch) c).withBody(
                        catzh.getBody().withStatements(
                                singletonList(new Tr.Throw(randomId(),
                                        new Tr.NewClass(randomId(),
                                                Tr.Ident.build(randomId(), "RuntimeException", Type.Class.build("java.lang.RuntimeException"), format(" ")),
                                                new Tr.NewClass.Arguments(randomId(),
                                                        singletonList(Tr.Ident.build(randomId(), catzh.getParam().getTree().getVars().iterator().next().getSimpleName(),
                                                                exceptionType == null ? null : exceptionType.getType(), EMPTY)),
                                                        EMPTY),
                                                null,
                                                Type.Class.build("java.lang.RuntimeException"),
                                                format(" ")
                                        ),
                                        INFER))
                        )
                );
            });
        }

        return super.visitCatch(catzh);
    }

    private boolean isEmpty(Tr.Block<?> blockNode) {
        return block.equals(BlockPolicy.Statement) && blockNode.getStatements().isEmpty();
    }
}
