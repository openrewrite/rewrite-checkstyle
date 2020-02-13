package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.AstVisitor;
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
                transform(block, b -> formatCurly(option, containing, spansMultipleLines, b))
        );
    }

    private boolean satisfiesPolicy(LeftCurlyPolicy option, Tr.Block<Tree> block, Tree containing, boolean spansMultipleLines) {
        boolean satisfiesPolicy;
        switch (option) {
            case EOL:
                satisfiesPolicy = (ignoreEnums && containing instanceof Tr.Case) || !block.getFormatting().getPrefix().contains("\n");
                break;
            case NL:
                satisfiesPolicy = block.getFormatting().getPrefix().contains("\n");
                break;
            case NLOW:
            default:
                satisfiesPolicy = (spansMultipleLines && satisfiesPolicy(NL, block, containing, spansMultipleLines)) ||
                        (!spansMultipleLines && satisfiesPolicy(EOL, block, containing, spansMultipleLines));
        }
        return satisfiesPolicy;
    }

    private static Tr.Block<Tree> formatCurly(LeftCurlyPolicy option, Cursor containing, boolean spansMultipleLines, Tr.Block<Tree> b) {
        switch (option) {
            case EOL:
                return containing.getParentOrThrow().getTree() instanceof Tr.ClassDecl && b.getStatic() == null ?
                        b : b.withFormatting(b.getFormatting().withPrefix(" "));
            case NL:
                return b.withFormatting(b.getFormatting().withPrefix(b.getEndOfBlockSuffix()));
            case NLOW:
            default:
                return formatCurly(spansMultipleLines ? NL : EOL, containing, spansMultipleLines, b);
        }
    }

    private static class SpansMultipleLines extends AstVisitor<Boolean> {
        @Override
        public Boolean defaultTo(Tree t) {
            return false;
        }

        @Override
        public Boolean visit(Tree tree) {
            return !(tree instanceof Tr.Block) && (tree.getFormatting().getPrefix().contains("\n") || super.visit(tree));
        }
    }
}
