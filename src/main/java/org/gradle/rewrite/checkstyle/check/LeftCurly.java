package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.LeftCurlyPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.List;
import java.util.Set;

import static org.gradle.rewrite.checkstyle.policy.LeftCurlyPolicy.EOL;
import static org.gradle.rewrite.checkstyle.policy.LeftCurlyPolicy.NL;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

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
        return "LeftCurly";
    }

    @Override
    public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
        Cursor containing = getCursor().getParentOrThrow();

        boolean spansMultipleLines = LeftCurlyPolicy.NLOW.equals(option) ?
                new SpansMultipleLines().visit((Tree) containing.getTree().withFormatting(Formatting.format(""))) : false;

        return maybeTransform(!satisfiesPolicy(option, block, containing.getTree(), spansMultipleLines),
                super.visitBlock(block),
                transform(block, b -> formatCurly(option, b, spansMultipleLines, containing))
        );
    }

    private boolean satisfiesPolicy(LeftCurlyPolicy option, Tr.Block<Tree> block, Tree containing, boolean spansMultipleLines) {
        switch (option) {
            case EOL:
                return (ignoreEnums && containing instanceof Tr.Case) || !block.getFormatting().getPrefix().contains("\n");
            case NL:
                return block.getFormatting().getPrefix().contains("\n");
            case NLOW:
            default:
                return (spansMultipleLines && satisfiesPolicy(NL, block, containing, spansMultipleLines)) ||
                        (!spansMultipleLines && satisfiesPolicy(EOL, block, containing, spansMultipleLines));
        }
    }

    private static Tr.Block<Tree> formatCurly(LeftCurlyPolicy option, Tr.Block<Tree> block, boolean spansMultipleLines, Cursor containing) {
        switch (option) {
            case EOL:
                return containing.getParentOrThrow().getTree() instanceof Tr.ClassDecl && block.getStatic() == null ?
                        block : block.withFormatting(block.getFormatting().withPrefix(" "));
            case NL:
                return block.withFormatting(block.getFormatting().withPrefix(block.getEndOfBlockSuffix()));
            case NLOW:
            default:
                return formatCurly(spansMultipleLines ? NL : EOL, block, spansMultipleLines, containing);
        }
    }
}
