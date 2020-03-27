package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.MethodDecl;
import org.openrewrite.java.tree.J.MethodInvocation;
import org.openrewrite.java.tree.J.NewClass;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.gradle.rewrite.checkstyle.policy.PadPolicy.NOSPACE;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

@Builder
public class MethodParamPad extends JavaRefactorVisitor {
    @Builder.Default
    private final boolean allowLineBreaks = false;

    @Builder.Default
    private final PadPolicy option = NOSPACE;

    @Builder.Default
    private final Set<Token> tokens = Set.of(
            CTOR_DEF, LITERAL_NEW, METHOD_CALL, METHOD_DEF, SUPER_CTOR_CALL, ENUM_CONSTANT_DEF
    );

    @Override
    public String getName() {
        return "checkstyle.MethodParamPad";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitMethod(MethodDecl method) {
        return maybeFixFormatting(method, super::visitMethod, MethodDecl::getParams, MethodDecl::withParams, METHOD_DEF);
    }

    @Override
    public J visitNewClass(NewClass newClass) {
        return maybeFixFormatting(newClass, super::visitNewClass, NewClass::getArgs, NewClass::withArgs, LITERAL_NEW);
    }

    @Override
    public J visitMethodInvocation(MethodInvocation method) {
        return maybeFixFormatting(method, super::visitMethodInvocation, MethodInvocation::getArgs, MethodInvocation::withArgs,
                METHOD_CALL, SUPER_CTOR_CALL);
    }

    private <T extends J, U extends Tree> T maybeFixFormatting(@Nullable T t, Function<T, Tree> callSuper,
                                                                Function<T, U> getter,
                                                                BiFunction<T, U, T> setter,
                                                                Token... tokensToMatch) {
        t = refactor(t, callSuper);

        if (getter.apply(t) != null && Token.matchesOneOf(tokens, getCursor(), tokensToMatch) && hasWrongSpacing(getter.apply(t))) {
            t = setter.apply(t, getter.apply(t).withPrefix(option == NOSPACE ? "" : " "));
        }

        return t;
    }

    private boolean hasWrongSpacing(Tree t) {
        String prefix = t.getFormatting().getPrefix();
        return option == NOSPACE ?
                !prefix.isEmpty() && (allowLineBreaks ?
                        prefix.startsWith(" ") || prefix.startsWith("\t") :
                        Character.isWhitespace(prefix.charAt(0))) :
                prefix.isEmpty();
    }
}
