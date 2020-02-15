package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy.ALONE;
import static org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy.SAME;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

@Builder
public class RightCurly extends RefactorVisitor {
    @Builder.Default
    private final RightCurlyPolicy option = ALONE;

    @Builder.Default
    private final Set<Token> tokens = Set.of(
            LITERAL_TRY, LITERAL_CATCH, LITERAL_FINALLY, LITERAL_IF, LITERAL_ELSE
    );

    @Override
    public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
        Cursor parentCursor = getCursor().getParentOrThrow();
        boolean tokenMatches = tokens.stream().anyMatch(t -> t.getMatcher().matches(parentCursor.getTree(), parentCursor.getParent())) ||
                parentCursor.getTree() instanceof Tr.Block;

        boolean satisfiesPolicy = block.getEndOfBlockSuffix().contains("\n") ||
                (option != ALONE && !new SpansMultipleLines().visit(block));

        return maybeTransform(tokenMatches && !satisfiesPolicy && parentCursor.enclosingBlock() != null,
                super.visitBlock(block),
                transform(block, (b, cursor) -> {
                    @SuppressWarnings("ConstantConditions") String suffix = formatter()
                            .findIndent(cursor.getParentOrThrow().enclosingBlock().getIndent(),
                                    singletonList(cursor.getParentOrThrow().getTree())).getPrefix();

                    Tr.Block<Tree> transformed = b.withEndOfBlockSuffix(suffix);

                    if(transformed.getStatements().size() == 1) {
                        transformed.getStatements().set(0, transformed.getStatements().get(0)
                                .withFormatting(formatter().format(transformed)));
                    }

                    return transformed;
                })
        );
    }

    @Override
    public List<AstTransform> visitElse(Tr.If.Else elze) {
        return maybeTransform(tokens.contains(LITERAL_ELSE) && !multiBlockSatisfiesPolicy(elze),
                super.visitElse(elze),
                transform(elze, this::formatMultiBlock)
        );
    }

    @Override
    public List<AstTransform> visitFinally(Tr.Try.Finally finallie) {
        return maybeTransform(tokens.contains(LITERAL_FINALLY) && !multiBlockSatisfiesPolicy(finallie),
                super.visitFinally(finallie),
                transform(finallie, this::formatMultiBlock)
        );
    }

    @Override
    public List<AstTransform> visitCatch(Tr.Try.Catch catzh) {
        return maybeTransform(tokens.contains(LITERAL_CATCH) && !multiBlockSatisfiesPolicy(catzh),
                super.visitCatch(catzh),
                transform(catzh, this::formatMultiBlock)
        );
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean multiBlockSatisfiesPolicy(Tree blockFollower) {
        boolean isAlone = blockFollower.getFormatting().getPrefix().contains("\n");
        return (option == SAME) != isAlone;
    }

    @SuppressWarnings("ConstantConditions")
    private <T extends Tree> T formatMultiBlock(T tree, Cursor cursor) {
        return option == SAME ?
                tree.withFormatting(tree.getFormatting().withPrefix(" ")) :
                tree.withFormatting(formatter().format(cursor.enclosingBlock()));
    }
}
