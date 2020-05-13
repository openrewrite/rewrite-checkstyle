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
package org.openrewrite.checkstyle.check;

import org.openrewrite.Tree;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import java.util.List;

import static org.openrewrite.checkstyle.check.internal.WhitespaceChecks.*;

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
