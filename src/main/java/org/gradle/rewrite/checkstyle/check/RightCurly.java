package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy.*;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

@Builder
public class RightCurly extends JavaRefactorVisitor {
    @Builder.Default
    private final RightCurlyPolicy option = ALONE;

    @Builder.Default
    private final Set<Token> tokens = Set.of(
            LITERAL_TRY, LITERAL_CATCH, LITERAL_FINALLY, LITERAL_IF, LITERAL_ELSE
    );

    @Override
    public String getName() {
        return "checkstyle.RightCurly";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public J visitBlock(J.Block<J> block) {
        J.Block<J> b = refactor(block, super::visitBlock);

        Cursor parentCursor = getCursor().getParentOrThrow();
        boolean tokenMatches = tokens.stream().anyMatch(t -> t.getMatcher().matches(getCursor())) ||
                (option != ALONE_OR_SINGLELINE && tokens.stream().anyMatch(t -> t.getMatcher().matches(parentCursor))) ||
                parentCursor.getTree() instanceof J.Block;

        boolean satisfiesPolicy = block.getEndOfBlockSuffix().contains("\n") ||
                (option != ALONE && !new SpansMultipleLines(block,null).visit(block));

        if (tokenMatches && !satisfiesPolicy && parentCursor.firstEnclosing(J.Block.class) != null) {
            String suffix = formatter.findIndent(getCursor().getParentOrThrow()
                            .firstEnclosing(J.Block.class).getIndent(),
                    getCursor().getParentOrThrow().getTree()).getPrefix();

            b = b.withEndOfBlockSuffix(suffix);

            if (b.getStatements().size() == 1) {
                b.getStatements().set(0, b.getStatements().get(0).withFormatting(formatter.format(b)));
            }
        }

        return b;
    }

    @Override
    public J visitElse(J.If.Else elze) {
        J.If.Else e = refactor(elze, super::visitElse);
        if (tokens.contains(LITERAL_ELSE) && !multiBlockSatisfiesPolicy(elze)) {
            e = formatMultiBlock(e);
        }
        return e;
    }

    @Override
    public J visitFinally(J.Try.Finally finallie) {
        J.Try.Finally f = refactor(finallie, super::visitFinally);
        if (tokens.contains(LITERAL_FINALLY) && !multiBlockSatisfiesPolicy(finallie)) {
            f = formatMultiBlock(f);
        }
        return f;
    }

    @Override
    public J visitCatch(J.Try.Catch catzh) {
        J.Try.Catch c = refactor(catzh, super::visitCatch);
        if (tokens.contains(LITERAL_CATCH) && !multiBlockSatisfiesPolicy(catzh)) {
            c = formatMultiBlock(c);
        }
        return c;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean multiBlockSatisfiesPolicy(Tree blockFollower) {
        boolean isAlone = blockFollower.getFormatting().getPrefix().contains("\n");
        return (option == SAME) != isAlone;
    }

    private <T extends J> T formatMultiBlock(T tree) {
        return option == SAME ?
                tree.withPrefix(" ") :
                tree.withFormatting(formatter.format(enclosingBlock()));
    }
}
