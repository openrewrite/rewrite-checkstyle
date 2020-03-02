package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Tree;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;

import java.util.List;

import static org.openrewrite.Formatting.*;

public class GenericWhitespace extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.GenericWhitespace";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitTypeParameters(J.TypeParameters typeParams) {
        J.TypeParameters t = refactor(typeParams, super::visitTypeParameters);

        Tree tree = getCursor().getParentOrThrow().getTree();
        if (!(tree instanceof J.MethodDecl) && !typeParams.getFormatting().getPrefix().isEmpty()) {
            t = t.withFormatting(EMPTY);
        }

        return t;
    }

    @Override
    public J visitTypeParameter(J.TypeParameter typeParam) {
        J.TypeParameter t = refactor(typeParam, super::visitTypeParameter);
        List<J.TypeParameter> params = ((J.TypeParameters) getCursor().getParentOrThrow().getTree()).getParams();

        if (params.isEmpty()) {
            return t;
        } else if (params.size() == 1) {
            if (!typeParam.getFormatting().equals(EMPTY)) {
                t = t.withFormatting(EMPTY);
            }
        } else if (params.get(0) == typeParam) {
            if (!typeParam.getFormatting().getPrefix().isEmpty()) {
                t = stripPrefix(t);
            }
        } else if (params.get(params.size() - 1) == typeParam) {
            if (!typeParam.getFormatting().getSuffix().isEmpty()) {
                t = stripSuffix(t);
            }
        }

        return t;
    }
}
