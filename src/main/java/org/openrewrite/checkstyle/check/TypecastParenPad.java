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

import lombok.RequiredArgsConstructor;
import org.openrewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.Formatting;
import org.openrewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;

import static org.openrewrite.checkstyle.policy.PadPolicy.NOSPACE;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;

@RequiredArgsConstructor
public class TypecastParenPad extends JavaRefactorVisitor {
    private final PadPolicy option;

    public TypecastParenPad() {
        this(PadPolicy.NOSPACE);
    }

    @Override
    public String getName() {
        return "checkstyle.TypecastParenPad";
    }

    @Override
    public J visitTypeCast(J.TypeCast typeCast) {
        J.TypeCast tc = refactor(typeCast, super::visitTypeCast);
        Formatting formatting = typeCast.getClazz().getTree().getFormatting();
        if((option == PadPolicy.NOSPACE) != formatting.equals(EMPTY)) {
            tc = tc.withClazz(tc.getClazz().withTree(tc.getClazz().getTree()
                    .withFormatting(option == PadPolicy.NOSPACE ? EMPTY : format(" ", " "))));
        }
        return tc;
    }
}
