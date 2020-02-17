package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;

import java.util.List;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Formatting.stripPrefix;

public class GenericWhitespace extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "GenericWhitespace";
    }

    @Override
    public List<AstTransform> visitTypeParameters(Tr.TypeParameters typeParams) {
        Tree tree = getCursor().getParentOrThrow().getTree();
        return maybeTransform(!(tree instanceof Tr.MethodDecl) && !typeParams.getFormatting().getPrefix().isEmpty(),
                super.visitTypeParameters(typeParams),
                transform(typeParams, tp -> tp.withFormatting(EMPTY))
        );
    }

    @Override
    public List<AstTransform> visitTypeParameter(Tr.TypeParameter typeParam) {
        Tr.TypeParameters typeParams = getCursor().getParentOrThrow().getTree();

        if (typeParams.getParams().size() == 1) {
            return maybeTransform(!typeParam.getFormatting().equals(EMPTY),
                    super.visitTypeParameter(typeParam),
                    transform(typeParam, tp -> tp.withFormatting(EMPTY))
            );
        } else if (typeParams.getParams().get(0) == typeParam) {
            return maybeTransform(!typeParam.getFormatting().getPrefix().isEmpty(),
                    super.visitTypeParameter(typeParam),
                    transform(typeParam, Formatting::stripPrefix)
            );
        } else if (typeParams.getParams().get(typeParams.getParams().size() - 1) == typeParam) {
            return maybeTransform(!typeParam.getFormatting().getSuffix().isEmpty(),
                    super.visitTypeParameter(typeParam),
                    transform(typeParam, Formatting::stripSuffix)
            );
        }

        return super.visitTypeParameter(typeParam);
    }
}
