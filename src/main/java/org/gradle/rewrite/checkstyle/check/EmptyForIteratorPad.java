package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;

import java.util.List;
import java.util.stream.Collectors;

import static com.netflix.rewrite.tree.Formatting.formatLastSuffix;
import static com.netflix.rewrite.tree.Formatting.lastSuffix;
import static java.util.stream.Collectors.toList;

@RequiredArgsConstructor
public class EmptyForIteratorPad extends RefactorVisitor {
    private final PadPolicy option;

    public EmptyForIteratorPad() {
        this(PadPolicy.NOSPACE);
    }

    @Override
    public String getRuleName() {
        return "EmptyForInitializerPad";
    }

    @Override
    public List<AstTransform> visitForLoop(Tr.ForLoop forLoop) {
        String suffix = lastSuffix(forLoop.getControl().getUpdate());
        return maybeTransform(!suffix.contains("\n") && option == PadPolicy.NOSPACE ?
                        suffix.endsWith(" ") || suffix.endsWith("\t") :
                        suffix.isEmpty(),
                super.visitForLoop(forLoop),
                transform(forLoop, f -> f.withControl(f.getControl().withUpdate(formatLastSuffix(
                        f.getControl().getUpdate(), option == PadPolicy.NOSPACE ? "" : " "))))
        );
    }
}
