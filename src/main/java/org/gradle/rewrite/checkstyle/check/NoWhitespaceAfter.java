package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.PunctuationToken;
import org.openrewrite.Formatting;
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.PunctuationToken.*;
import static org.openrewrite.Formatting.*;

@Builder
public class NoWhitespaceAfter extends JavaRefactorVisitor {
    /**
     * Only applies to DOT.
     */
    @Builder.Default
    private final boolean allowLineBreaks = true;

    @Builder.Default
    private final Set<PunctuationToken> tokens = Set.of(
            ARRAY_INIT, AT, INC, DEC, UNARY_MINUS, UNARY_PLUS, BNOT, LNOT, DOT, ARRAY_DECLARATOR, INDEX_OP
    );

    @Override
    public String getName() {
        return "checkstyle.NoWhitespaceAfter";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitTypeCast(J.TypeCast typeCast) {
        J.TypeCast t = refactor(typeCast, super::visitTypeCast);
        if (tokens.contains(TYPECAST) && whitespaceInPrefix(typeCast.getExpr())) {
            t = t.withExpr(stripPrefix(typeCast.getExpr()));
        }
        return t;
    }

    @Override
    public J visitMemberReference(J.MemberReference memberRef) {
        J.MemberReference m = refactor(memberRef, super::visitMemberReference);
        if (tokens.contains(METHOD_REF) && whitespaceInPrefix(memberRef.getReference())) {
            m = m.withReference(stripPrefix(memberRef.getReference()));
        }
        return m;
    }

    @Override
    public J visitMultiVariable(J.VariableDecls multiVariable) {
        J.VariableDecls m = refactor(multiVariable, super::visitMultiVariable);
        if (tokens.contains(ARRAY_DECLARATOR) && multiVariable.getDimensionsBeforeName().stream().anyMatch(this::whitespaceInPrefix)) {
            m = m.withDimensionsBeforeName(m.getDimensionsBeforeName().stream()
                    .map(Formatting::stripPrefix).collect(toList()));
        }
        return m;
    }

    @Override
    public J visitAnnotation(J.Annotation annotation) {
        J.Annotation a = refactor(annotation, super::visitAnnotation);
        if (tokens.contains(AT) && whitespaceInPrefix(annotation.getAnnotationType())) {
            a = a.withAnnotationType(stripPrefix(a.getAnnotationType()));
        }
        return a;
    }

    @Override
    public J visitArrayType(J.ArrayType arrayType) {
        J.ArrayType a = refactor(arrayType, super::visitArrayType);
        if (tokens.contains(ARRAY_DECLARATOR) && arrayType.getDimensions().stream().anyMatch(this::whitespaceInPrefix)) {
            a = a.withDimensions(a.getDimensions().stream()
                    .map(Formatting::stripPrefix).collect(toList()));
        }
        return a;
    }

    @Override
    public J visitNewArray(J.NewArray newArray) {
        J.NewArray n = refactor(newArray, super::visitNewArray);
        if (tokens.contains(ARRAY_INIT) &&
                getCursor().firstEnclosing(J.Annotation.class) == null &&
                Optional.ofNullable(newArray.getInitializer())
                        .map(J.NewArray.Initializer::getElements)
                        .map(init -> !init.isEmpty() &&
                                (whitespaceInPrefix(init.get(0)) || whitespaceInSuffix(init.get(init.size() - 1))))
                        .orElse(false)) {
            @SuppressWarnings("ConstantConditions") List<Expression> fixedInit =
                    new ArrayList<>(n.getInitializer().getElements());

            if (fixedInit.size() == 1) {
                fixedInit.set(0, fixedInit.get(0).withFormatting(EMPTY));
            } else {
                fixedInit.set(0, stripPrefix(fixedInit.get(0)));
                fixedInit.set(fixedInit.size() - 1, stripSuffix(fixedInit.get(fixedInit.size() - 1)));
            }

            n = n.withInitializer(n.getInitializer().withElements(fixedInit));
        }
        return n;
    }

    @Override
    public J visitArrayAccess(J.ArrayAccess arrayAccess) {
        J.ArrayAccess a = refactor(arrayAccess, super::visitArrayAccess);
        if (tokens.contains(INDEX_OP) && whitespaceInPrefix(arrayAccess.getDimension())) {
            a = a.withDimension(stripPrefix(a.getDimension()));
        }
        return a;
    }

    @Override
    public J visitUnary(J.Unary unary) {
        J.Unary u = refactor(unary, super::visitUnary);

        J.Unary.Operator op = unary.getOperator();
        if (op instanceof J.Unary.Operator.PreDecrement ||
                op instanceof J.Unary.Operator.PreIncrement ||
                op instanceof J.Unary.Operator.Negative ||
                op instanceof J.Unary.Operator.Positive ||
                op instanceof J.Unary.Operator.Complement ||
                op instanceof J.Unary.Operator.Not) {

            if ((tokens.contains(DEC) ||
                    tokens.contains(INC) ||
                    tokens.contains(BNOT) ||
                    tokens.contains(LNOT) ||
                    tokens.contains(UNARY_PLUS) ||
                    tokens.contains(UNARY_MINUS)) && whitespaceInPrefix(unary.getExpr())) {
                u = u.withExpr(stripPrefix(u.getExpr()));
            }
        }

        return u;
    }

    @Override
    public J visitFieldAccess(J.FieldAccess fieldAccess) {
        J.FieldAccess f = refactor(fieldAccess, super::visitFieldAccess);
        if (tokens.contains(DOT) && whitespaceInDotPrefix(fieldAccess.getName())) {
            f = f.withName(stripPrefix(f.getName()));
        }
        return f;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);
        if (tokens.contains(DOT) && whitespaceInDotPrefix(method.getName())) {
            m = m.withName(stripPrefix(m.getName()));
        }
        return m;
    }

    private boolean whitespaceInSuffix(@Nullable Tree t) {
        return t != null && (t.getFormatting().getSuffix().contains(" ") || t.getFormatting().getSuffix().contains("\t"));
    }

    private boolean whitespaceInDotPrefix(@Nullable Tree t) {
        return whitespaceInPrefix(t) && (!allowLineBreaks || !t.getFormatting().getPrefix().startsWith("\n"));
    }

    private boolean whitespaceInPrefix(@Nullable Tree t) {
        if (t == null) {
            return false;
        }
        String prefix = t.getFormatting().getPrefix();
        return (prefix.contains(" ") || prefix.contains("\t"));
    }
}
