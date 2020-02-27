package org.gradle.rewrite.checkstyle.check;

import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.tree.Formatting;
import org.openrewrite.tree.J;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static org.gradle.rewrite.checkstyle.policy.PadPolicy.NOSPACE;
import static org.openrewrite.tree.Formatting.EMPTY;
import static org.openrewrite.tree.Formatting.format;

@RequiredArgsConstructor
public class TypecastParenPad extends RefactorVisitor {
    private final PadPolicy option;

    public TypecastParenPad() {
        this(NOSPACE);
    }

    @Override
    public String getRuleName() {
        return "checkstyle.TypecastParenPad";
    }

    @Override
    public List<AstTransform> visitTypeCast(J.TypeCast typeCast) {
        Formatting formatting = typeCast.getClazz().getTree().getFormatting();
        return maybeTransform(typeCast,
                (option == NOSPACE) != formatting.equals(EMPTY),
                super::visitTypeCast,
                tc -> tc.withClazz(tc.getClazz().withTree(tc.getClazz().getTree()
                        .withFormatting(option == NOSPACE ? EMPTY : format(" ", " ")))));
    }
}
