package org.gradle.rewrite.checkstyle.check;

import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.tree.J;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static org.openrewrite.tree.Formatting.formatLastSuffix;
import static org.openrewrite.tree.Formatting.lastSuffix;

@RequiredArgsConstructor
public class EmptyForIteratorPad extends RefactorVisitor {
    private final PadPolicy option;

    public EmptyForIteratorPad() {
        this(PadPolicy.NOSPACE);
    }

    @Override
    public String getRuleName() {
        return "checkstyle.EmptyForInitializerPad";
    }

    @Override
    public List<AstTransform> visitForLoop(J.ForLoop forLoop) {
        String suffix = lastSuffix(forLoop.getControl().getUpdate());
        return maybeTransform(forLoop,
                !suffix.contains("\n") &&
                        (option == PadPolicy.NOSPACE ? suffix.endsWith(" ") || suffix.endsWith("\t") : suffix.isEmpty()) &&
                        forLoop.getControl().getUpdate().stream().reduce((u1, u2) -> u2).map(u -> u instanceof J.Empty).orElse(false),
                super::visitForLoop,
                c -> c.withControl(c.getControl().withUpdate(formatLastSuffix(c.getControl().getUpdate(), option == PadPolicy.NOSPACE ? "" : " ")))
        );
    }
}
