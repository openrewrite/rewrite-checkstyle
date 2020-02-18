package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr.EnumValue;
import com.netflix.rewrite.tree.Tr.MethodDecl;
import com.netflix.rewrite.tree.Tr.MethodInvocation;
import com.netflix.rewrite.tree.Tr.NewClass;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.PadPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.gradle.rewrite.checkstyle.policy.PadPolicy.NOSPACE;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

@Builder
public class MethodParamPad extends RefactorVisitor {
    @Builder.Default
    private final boolean allowLineBreaks = false;

    @Builder.Default
    private final PadPolicy option = NOSPACE;

    @Builder.Default
    private final Set<Token> tokens = Set.of(
            CTOR_DEF, LITERAL_NEW, METHOD_CALL, METHOD_DEF, SUPER_CTOR_CALL, ENUM_CONSTANT_DEF
    );

    @Override
    public String getRuleName() {
        return "MethodParamPad";
    }

    @Override
    public List<AstTransform> visitMethod(MethodDecl method) {
        return maybeFixFormatting(method, super::visitMethod, MethodDecl::getParams, MethodDecl::withParams, METHOD_DEF);
    }

    @Override
    public List<AstTransform> visitNewClass(NewClass newClass) {
        return maybeFixFormatting(newClass, super::visitNewClass, NewClass::getArgs, NewClass::withArgs, LITERAL_NEW);
    }

    @Override
    public List<AstTransform> visitEnumValue(EnumValue enoom) {
        return maybeFixFormatting(enoom, super::visitEnumValue, EnumValue::getInitializer, EnumValue::withInitializer, ENUM_CONSTANT_DEF);
    }

    @Override
    public List<AstTransform> visitMethodInvocation(MethodInvocation method) {
        return maybeFixFormatting(method, super::visitMethodInvocation, MethodInvocation::getArgs, MethodInvocation::withArgs,
                METHOD_CALL, SUPER_CTOR_CALL);
    }

    private <T extends Tree, U extends Tree> List<AstTransform> maybeFixFormatting(T t,
                                                                                   Function<T, List<AstTransform>> callSuper,
                                                                                   Function<T, U> getter,
                                                                                   BiFunction<T, U, T> setter,
                                                                                   Token... tokensToMatch) {
        return maybeTransform(t,
                Token.matchesOneOf(tokens, getCursor(), tokensToMatch) &&
                        hasWrongSpacing(getter.apply(t)) &&
                        getter.apply(t) != null,
                callSuper,
                t2 -> setter.apply(t2, getter.apply(t2).withPrefix(option == NOSPACE ? "" : " "))
        );
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
