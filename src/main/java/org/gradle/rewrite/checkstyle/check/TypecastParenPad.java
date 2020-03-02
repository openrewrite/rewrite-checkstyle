package org.gradle.rewrite.checkstyle.check;

import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.Formatting;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;

import static org.gradle.rewrite.checkstyle.policy.PadPolicy.NOSPACE;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;

@RequiredArgsConstructor
public class TypecastParenPad extends JavaRefactorVisitor {
    private final PadPolicy option;

    public TypecastParenPad() {
        this(NOSPACE);
    }

    @Override
    public String getName() {
        return "checkstyle.TypecastParenPad";
    }

    @Override
    public J visitTypeCast(J.TypeCast typeCast) {
        J.TypeCast tc = refactor(typeCast, super::visitTypeCast);
        Formatting formatting = typeCast.getClazz().getTree().getFormatting();
        if((option == NOSPACE) != formatting.equals(EMPTY)) {
            tc = tc.withClazz(tc.getClazz().withTree(tc.getClazz().getTree()
                    .withFormatting(option == NOSPACE ? EMPTY : format(" ", " "))));
        }
        return tc;
    }
}
