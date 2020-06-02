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
import org.openrewrite.Tree;
import org.openrewrite.checkstyle.policy.PadPolicy;
import org.openrewrite.checkstyle.policy.Token;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDecl;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.J.NewClass;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MethodParamPad extends CheckstyleRefactorVisitor {
    private static final Set<Token> DEFAULT_TOKENS = Set.of(
            Token.CTOR_DEF, Token.LITERAL_NEW, Token.METHOD_CALL, Token.METHOD_DEF, Token.SUPER_CTOR_CALL, Token.ENUM_CONSTANT_DEF
    );

    private final boolean allowLineBreaks;
    private final PadPolicy option;
    private final Set<Token> tokens;

    public MethodParamPad(boolean allowLineBreaks, PadPolicy option, Set<Token> tokens) {
        super("checkstyle.MethodParamPad");
        this.allowLineBreaks = allowLineBreaks;
        this.option = option;
        this.tokens = tokens;
        setCursoringOn();
    }

    @AutoConfigure
    public static MethodParamPad configure(Config config) {
        return fromModule(
                config,
                "MethodParamPad",
                m -> new MethodParamPad(
                        m.prop("allowLineBreaks", false),
                        m.propAsOptionValue(PadPolicy::valueOf, PadPolicy.NOSPACE),
                        m.propAsTokens(Token.class, DEFAULT_TOKENS)
                )
        );
    }

    @Override
    public J visitMethod(MethodDecl method) {
        return maybeFixFormatting(method, super::visitMethod, MethodDecl::getParams, MethodDecl::withParams, Token.METHOD_DEF);
    }

    @Override
    public J visitNewClass(NewClass newClass) {
        return maybeFixFormatting(newClass, super::visitNewClass, NewClass::getArgs, NewClass::withArgs, Token.LITERAL_NEW);
    }

    @Override
    public J visitMethodInvocation(MethodInvocation method) {
        return maybeFixFormatting(method, super::visitMethodInvocation, MethodInvocation::getArgs, MethodInvocation::withArgs,
                Token.METHOD_CALL, Token.SUPER_CTOR_CALL);
    }

    private <T extends J, U extends Tree> T maybeFixFormatting(@Nullable T t, Function<T, Tree> callSuper,
                                                               Function<T, U> getter,
                                                               BiFunction<T, U, T> setter,
                                                               Token... tokensToMatch) {
        t = refactor(t, callSuper);

        if (getter.apply(t) != null && Token.matchesOneOf(tokens, getCursor(), tokensToMatch) && hasWrongSpacing(getter.apply(t))) {
            t = setter.apply(t, getter.apply(t).withPrefix(option == PadPolicy.NOSPACE ? "" : " "));
        }

        return t;
    }

    private boolean hasWrongSpacing(Tree t) {
        String prefix = t.getFormatting().getPrefix();
        return option == PadPolicy.NOSPACE ?
                !prefix.isEmpty() && (allowLineBreaks ?
                        prefix.startsWith(" ") || prefix.startsWith("\t") :
                        Character.isWhitespace(prefix.charAt(0))) :
                prefix.isEmpty();
    }
}
