package org.gradle.rewrite.checkstyle.check;

import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import static org.openrewrite.Formatting.formatLastSuffix;
import static org.openrewrite.Formatting.lastSuffix;

@RequiredArgsConstructor
public class EmptyForIteratorPad extends JavaRefactorVisitor {
    private final PadPolicy option;

    public EmptyForIteratorPad() {
        this(PadPolicy.NOSPACE);
    }

    @Override
    public String getName() {
        return "checkstyle.EmptyForInitializerPad";
    }

    @Override
    public J visitForLoop(J.ForLoop forLoop) {
        J.ForLoop f = refactor(forLoop, super::visitForLoop);
        String suffix = lastSuffix(forLoop.getControl().getUpdate());

        if (!suffix.contains("\n") &&
                (option == PadPolicy.NOSPACE ? suffix.endsWith(" ") || suffix.endsWith("\t") : suffix.isEmpty()) &&
                forLoop.getControl().getUpdate().stream().reduce((u1, u2) -> u2).map(u -> u instanceof J.Empty).orElse(false)) {
            f = f.withControl(f.getControl().withUpdate(formatLastSuffix(f.getControl().getUpdate(), option == PadPolicy.NOSPACE ? "" : " ")));
        }

        return f;
    }
}
