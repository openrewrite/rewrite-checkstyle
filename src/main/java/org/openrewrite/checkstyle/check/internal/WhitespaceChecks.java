/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.checkstyle.check.internal;

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
