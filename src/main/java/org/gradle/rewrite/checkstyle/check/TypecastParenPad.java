package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;

import java.util.List;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Formatting.format;
import static org.gradle.rewrite.checkstyle.policy.PadPolicy.NOSPACE;

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
    public List<AstTransform> visitTypeCast(Tr.TypeCast typeCast) {
        Formatting formatting = typeCast.getClazz().getTree().getFormatting();
        return maybeTransform(typeCast,
                (option == NOSPACE) != formatting.equals(EMPTY),
                super::visitTypeCast,
                tc -> tc.withClazz(tc.getClazz().withTree(tc.getClazz().getTree()
                        .withFormatting(option == NOSPACE ? EMPTY : format(" ", " ")))));
    }
}
