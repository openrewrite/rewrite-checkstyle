package org.gradle.rewrite.checkstyle.check.internal;

import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;

public class WhitespaceChecks {
    private WhitespaceChecks() {
    }

    public static boolean prefixStartsWithNonLinebreakWhitespace(@Nullable Tree t) {
        return t != null && startsWithNonLinebreakWhitespace(t.getFormatting().getPrefix());
    }

    public static boolean suffixStartsWithNonLinebreakWhitespace(@Nullable Tree t) {
        return t != null && startsWithNonLinebreakWhitespace(t.getFormatting().getSuffix());
    }

    public static boolean startsWithNonLinebreakWhitespace(String prefixOrSuffix) {
        return prefixOrSuffix.startsWith(" ") || prefixOrSuffix.startsWith("\t");
    }

    public static <T extends Tree> T stripSuffixUpToLinebreak(@Nullable T t) {
        return t == null ? null : t.withSuffix(stripUpToLinebreak(t.getFormatting().getSuffix()));
    }

    public static <T extends Tree> T stripPrefixUpToLinebreak(@Nullable T t) {
        return t == null ? null : t.withPrefix(stripUpToLinebreak(t.getFormatting().getPrefix()));
    }

    public static String stripUpToLinebreak(String prefixOrSuffix) {
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
