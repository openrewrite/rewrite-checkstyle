package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

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
        if (!(tree instanceof J.MethodDecl)) {
            if(startsWithNonLinebreakWhitespace(t.getFormatting().getPrefix())) {
                t = stripPrefixUpToLinebreak(t);
            }
            if(startsWithNonLinebreakWhitespace(t.getFormatting().getSuffix())) {
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
            if (startsWithNonLinebreakWhitespace(t.getFormatting().getPrefix())) {
                t = stripPrefixUpToLinebreak(t);
            }
            if (startsWithNonLinebreakWhitespace(t.getFormatting().getSuffix())) {
                t = stripSuffixUpToLinebreak(t);
            }
        } else if (params.get(0) == t) {
            if (startsWithNonLinebreakWhitespace(t.getFormatting().getPrefix())) {
                t = stripPrefixUpToLinebreak(t);
            }
        } else if (params.get(params.size() - 1) == t) {
            if (startsWithNonLinebreakWhitespace(t.getFormatting().getSuffix())) {
                t = stripSuffixUpToLinebreak(t);
            }
        }

        return t;
    }

    private static boolean startsWithNonLinebreakWhitespace(String prefixOrSuffix) {
        return prefixOrSuffix.startsWith(" ") || prefixOrSuffix.startsWith("\t");
    }

    private static <T extends Tree> T stripSuffixUpToLinebreak(@Nullable T t) {
        return t == null ? null : t.withSuffix(stripUpToLinebreak(t.getFormatting().getSuffix()));
    }

    private static <T extends Tree> T stripPrefixUpToLinebreak(@Nullable T t) {
        return t == null ? null : t.withPrefix(stripUpToLinebreak(t.getFormatting().getPrefix()));
    }

    private static String stripUpToLinebreak(String prefixOrSuffix) {
        StringBuilder sb = new StringBuilder();
        boolean drop = true;
        for (char c : prefixOrSuffix.toCharArray()) {
            drop &= (c == ' ' || c == '\t');
            if(!drop) {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
