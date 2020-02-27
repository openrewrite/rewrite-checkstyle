package org.gradle.rewrite.checkstyle.check;

import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Statement;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

@RequiredArgsConstructor
public class EmptyForInitializerPad extends RefactorVisitor {
    private final PadPolicy option;

    public EmptyForInitializerPad() {
        this(PadPolicy.NOSPACE);
    }

    @Override
    public String getRuleName() {
        return "checkstyle.EmptyForInitializerPad";
    }

    @Override
    public List<AstTransform> visitForLoop(J.ForLoop forLoop) {
        String prefix = forLoop.getControl().getInit().getFormatting().getPrefix();
        return maybeTransform(forLoop,
                !prefix.startsWith("\n") &&
                        (option == PadPolicy.NOSPACE ? prefix.startsWith(" ") || prefix.startsWith("\t") : prefix.isEmpty()) &&
                        forLoop.getControl().getInit() instanceof J.Empty,
                super::visitForLoop,
                f -> {
                    Statement init = f.getControl().getInit();
                    String fixedPrefix = option == PadPolicy.NOSPACE ? "" : " ";
                    return f.withControl(f.getControl().withInit(init.withPrefix(fixedPrefix)));
                }
        );
    }
}
