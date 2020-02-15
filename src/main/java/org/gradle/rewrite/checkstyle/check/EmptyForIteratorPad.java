package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;

import java.util.List;
import java.util.stream.Collectors;

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
        List<Statement> update = forLoop.getControl().getUpdate();
        Statement lastUpdate = update.get(update.size() - 1);
        String suffix = lastUpdate.getFormatting().getSuffix();
        return maybeTransform(!suffix.contains("\n") && option == PadPolicy.NOSPACE ?
                        suffix.endsWith(" ") || suffix.endsWith("\t") :
                        suffix.isEmpty(),
                super.visitForLoop(forLoop),
                transform(forLoop, f -> {
                    String fixedSuffix = option == PadPolicy.NOSPACE ? "" : " ";
                    return f.withControl(f.getControl().withUpdate(
                            f.getControl().getUpdate().stream()
                                    .map(u -> u.getId().equals(lastUpdate.getId()) ?
                                            u.withFormatting(u.getFormatting().withSuffix(fixedSuffix)) :
                                            u)
                                    .collect(toList())
                    ));
                })
        );
    }
}
