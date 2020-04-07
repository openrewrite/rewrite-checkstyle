package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Tree;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.util.List;

import static org.gradle.rewrite.checkstyle.check.internal.WhitespaceChecks.*;

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
        if (!(tree instanceof J.MethodDecl)) {
            if(prefixStartsWithNonLinebreakWhitespace(t)) {
                t = stripPrefixUpToLinebreak(t);
            }
            if(suffixStartsWithNonLinebreakWhitespace(t)) {
                t = stripPrefixUpToLinebreak(t);
            }
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
            if (prefixStartsWithNonLinebreakWhitespace(t)) {
                t = stripPrefixUpToLinebreak(t);
            }
            if (suffixStartsWithNonLinebreakWhitespace(t)) {
                t = stripSuffixUpToLinebreak(t);
            }
        } else if (params.get(0) == t) {
            if (prefixStartsWithNonLinebreakWhitespace(t)) {
                t = stripPrefixUpToLinebreak(t);
            }
        } else if (params.get(params.size() - 1) == t) {
            if (suffixStartsWithNonLinebreakWhitespace(t)) {
                t = stripSuffixUpToLinebreak(t);
            }
        }

        return t;
    }
}
