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

import org.eclipse.microprofile.config.Config;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.java.tree.J;

import static org.openrewrite.Formatting.formatLastSuffix;
import static org.openrewrite.Formatting.lastSuffix;

public class EmptyForIteratorPad extends CheckstyleRefactorVisitor {
    private final PadPolicy option;

    public EmptyForIteratorPad(PadPolicy option) {
        super("checkstyle.EmptyForInitializerPad");
        this.option = option;
    }

    @AutoConfigure
    public static EmptyForIteratorPad configure(Config config) {
        return fromModule(
                config,
                "EmptyForIteratorPad",
                m -> new EmptyForIteratorPad(m.propAsOptionValue(PadPolicy::valueOf, PadPolicy.NOSPACE))
        );
    }

    @Override
    public String getName() {
        return "checkstyle.EmptyForInitializerPad";
    }

    @Override
    public J visitForLoop(J.ForLoop forLoop) {
        J.ForLoop f = refactor(forLoop, super::visitForLoop);
        String suffix = lastSuffix(forLoop.getControl().getUpdate());

        if (!suffix.contains("\n") &&
                (option == PadPolicy.NOSPACE ? suffix.endsWith(" ") || suffix.endsWith("\t") : suffix.isEmpty()) &&
                forLoop.getControl().getUpdate().stream().reduce((u1, u2) -> u2).map(u -> u instanceof J.Empty).orElse(false)) {
            f = f.withControl(f.getControl().withUpdate(formatLastSuffix(f.getControl().getUpdate(), option == PadPolicy.NOSPACE ? "" : " ")));
        }

        return f;
    }
}
