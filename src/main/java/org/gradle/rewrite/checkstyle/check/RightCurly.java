package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.tree.Cursor;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.Set;

import static org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy.*;
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
    public String getRuleName() {
        return "checkstyle.RightCurly";
    }

    @Override
    public List<AstTransform> visitBlock(J.Block<Tree> block) {
        Cursor parentCursor = getCursor().getParentOrThrow();
        boolean tokenMatches = tokens.stream().anyMatch(t -> t.getMatcher().matches(getCursor())) ||
                (option != ALONE_OR_SINGLELINE && tokens.stream().anyMatch(t -> t.getMatcher().matches(parentCursor))) ||
                parentCursor.getTree() instanceof J.Block;

        boolean satisfiesPolicy = block.getEndOfBlockSuffix().contains("\n") ||
                (option != ALONE && !new SpansMultipleLines(null).visit(block));

        return maybeTransform(block,
                tokenMatches && !satisfiesPolicy && parentCursor.enclosingBlock() != null,
                super::visitBlock,
                (b, cursor) -> {
                    String suffix = formatter().findIndent(cursor.getParentOrThrow().enclosingBlock().getIndent(),
                                    cursor.getParentOrThrow().getTree()).getPrefix();

                    J.Block<Tree> transformed = b.withEndOfBlockSuffix(suffix);

                    if(transformed.getStatements().size() == 1) {
                        transformed.getStatements().set(0, transformed.getStatements().get(0)
                                .withFormatting(formatter().format(transformed)));
                    }

                    return transformed;
                }
        );
    }

    @Override
    public List<AstTransform> visitElse(J.If.Else elze) {
        return maybeTransform(elze,
                tokens.contains(LITERAL_ELSE) && !multiBlockSatisfiesPolicy(elze),
                super::visitElse,
                this::formatMultiBlock);
    }

    @Override
    public List<AstTransform> visitFinally(J.Try.Finally finallie) {
        return maybeTransform(finallie,
                tokens.contains(LITERAL_FINALLY) && !multiBlockSatisfiesPolicy(finallie),
                super::visitFinally,
                this::formatMultiBlock);
    }

    @Override
    public List<AstTransform> visitCatch(J.Try.Catch catzh) {
        return maybeTransform(catzh,
                tokens.contains(LITERAL_CATCH) && !multiBlockSatisfiesPolicy(catzh),
                super::visitCatch,
                this::formatMultiBlock);
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean multiBlockSatisfiesPolicy(Tree blockFollower) {
        boolean isAlone = blockFollower.getFormatting().getPrefix().contains("\n");
        return (option == SAME) != isAlone;
    }

    private <T extends Tree> T formatMultiBlock(T tree, Cursor cursor) {
        return option == SAME ?
                tree.withPrefix(" ") :
                tree.withFormatting(formatter().format(cursor.enclosingBlock()));
    }
}
