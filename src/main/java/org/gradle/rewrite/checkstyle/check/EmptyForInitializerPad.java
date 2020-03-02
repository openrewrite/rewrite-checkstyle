package org.gradle.rewrite.checkstyle.check;

import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

@RequiredArgsConstructor
public class EmptyForInitializerPad extends JavaRefactorVisitor {
    private final PadPolicy option;

    public EmptyForInitializerPad() {
        this(PadPolicy.NOSPACE);
    }

    @Override
    public String getName() {
        return "checkstyle.EmptyForInitializerPad";
    }

    @Override
    public J visitForLoop(J.ForLoop forLoop) {
        J.ForLoop f = refactor(forLoop, super::visitForLoop);
        String prefix = forLoop.getControl().getInit().getFormatting().getPrefix();

        if (!prefix.startsWith("\n") &&
                (option == PadPolicy.NOSPACE ? prefix.startsWith(" ") || prefix.startsWith("\t") : prefix.isEmpty()) &&
                forLoop.getControl().getInit() instanceof J.Empty) {
            Statement init = f.getControl().getInit();
            String fixedPrefix = option == PadPolicy.NOSPACE ? "" : " ";
            f = f.withControl(f.getControl().withInit(init.withPrefix(fixedPrefix)));
        }

        return f;
    }
}
