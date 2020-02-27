package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.LeftCurlyPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.tree.Cursor;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.Set;

import static org.gradle.rewrite.checkstyle.policy.LeftCurlyPolicy.EOL;
import static org.gradle.rewrite.checkstyle.policy.LeftCurlyPolicy.NL;
import static org.gradle.rewrite.checkstyle.policy.Token.*;
import static org.openrewrite.tree.Formatting.EMPTY;

@Builder
public class LeftCurly extends RefactorVisitor {
    @Builder.Default
    private final LeftCurlyPolicy option = EOL;

    @Builder.Default
    private final boolean ignoreEnums = true;

    @Builder.Default
    private final Set<Token> tokens = Set.of(
            ANNOTATION_DEF,
            CLASS_DEF,
            CTOR_DEF,
            ENUM_CONSTANT_DEF,
            ENUM_DEF,
            INTERFACE_DEF,
            LAMBDA,
            LITERAL_CASE,
            LITERAL_CATCH,
            LITERAL_DEFAULT,
            LITERAL_DO,
            LITERAL_ELSE,
            LITERAL_FINALLY,
            LITERAL_FOR,
            LITERAL_IF,
            LITERAL_SWITCH,
            LITERAL_SYNCHRONIZED,
            LITERAL_TRY,
            LITERAL_WHILE,
            METHOD_DEF,
            OBJBLOCK,
            STATIC_INIT
    );

    @Override
    public String getRuleName() {
        return "checkstyle.LeftCurly";
    }

    @Override
    public List<AstTransform> visitBlock(J.Block<Tree> block) {
        Cursor containing = getCursor().getParentOrThrow();

        boolean spansMultipleLines = LeftCurlyPolicy.NLOW.equals(option) ?
                new SpansMultipleLines(block).visit((Tree) containing.getTree().withFormatting(EMPTY)) : false;

        return maybeTransform(block,
                !satisfiesPolicy(option, block, containing.getTree(), spansMultipleLines),
                super::visitBlock,
                b -> formatCurly(option, b, spansMultipleLines, containing)
        );
    }

    private boolean satisfiesPolicy(LeftCurlyPolicy option, J.Block<Tree> block, Tree containing, boolean spansMultipleLines) {
        switch (option) {
            case EOL:
                return (ignoreEnums && containing instanceof J.Case) || !block.getFormatting().getPrefix().contains("\n");
            case NL:
                return block.getFormatting().getPrefix().contains("\n");
            case NLOW:
            default:
                return (spansMultipleLines && satisfiesPolicy(NL, block, containing, spansMultipleLines)) ||
                        (!spansMultipleLines && satisfiesPolicy(EOL, block, containing, spansMultipleLines));
        }
    }

    private static J.Block<Tree> formatCurly(LeftCurlyPolicy option, J.Block<Tree> block, boolean spansMultipleLines, Cursor containing) {
        switch (option) {
            case EOL:
                return containing.getParentOrThrow().getTree() instanceof J.ClassDecl && block.getStatic() == null ?
                        block : block.withPrefix(" ");
            case NL:
                return block.withPrefix(block.getEndOfBlockSuffix());
            case NLOW:
            default:
                return formatCurly(spansMultipleLines ? NL : EOL, block, spansMultipleLines, containing);
        }
    }
}
