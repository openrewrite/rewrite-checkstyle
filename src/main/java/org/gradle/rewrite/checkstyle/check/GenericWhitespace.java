package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.Formatting;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;

import static org.openrewrite.tree.Formatting.EMPTY;

public class GenericWhitespace extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.GenericWhitespace";
    }

    @Override
    public List<AstTransform> visitTypeParameters(J.TypeParameters typeParams) {
        Tree tree = getCursor().getParentOrThrow().getTree();
        return maybeTransform(typeParams,
                !(tree instanceof J.MethodDecl) && !typeParams.getFormatting().getPrefix().isEmpty(),
                super::visitTypeParameters,
                tp -> tp.withFormatting(EMPTY)
        );
    }

    @Override
    public List<AstTransform> visitTypeParameter(J.TypeParameter typeParam) {
        List<J.TypeParameter> params = ((J.TypeParameters) getCursor().getParentOrThrow().getTree()).getParams();

        if (params.isEmpty()) {
            return super.visitTypeParameter(typeParam);
        } else if (params.size() == 1) {
            return maybeTransform(typeParam,
                    !typeParam.getFormatting().equals(EMPTY),
                    super::visitTypeParameter,
                    tp -> tp.withFormatting(EMPTY));
        } else if (params.get(0) == typeParam) {
            return maybeTransform(typeParam,
                    !typeParam.getFormatting().getPrefix().isEmpty(),
                    super::visitTypeParameter,
                    Formatting::stripPrefix);
        } else if (params.get(params.size() - 1) == typeParam) {
            return maybeTransform(typeParam,
                    !typeParam.getFormatting().getSuffix().isEmpty(),
                    super::visitTypeParameter,
                    Formatting::stripSuffix);
        }

        return super.visitTypeParameter(typeParam);
    }
}
