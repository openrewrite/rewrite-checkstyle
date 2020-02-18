package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.internal.lang.Nullable;
import com.netflix.rewrite.tree.Expression;
import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.PunctuationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.*;
import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.PunctuationToken.*;

@Builder
public class NoWhitespaceAfter extends RefactorVisitor {
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
    public String getRuleName() {
        return "NoWhitespaceAfter";
    }

    @Override
    public List<AstTransform> visitTypeCast(Tr.TypeCast typeCast) {
        return maybeTransform(typeCast,
                tokens.contains(TYPECAST) && whitespaceInPrefix(typeCast.getExpr()),
                super::visitTypeCast,
                tc -> tc.withExpr(stripPrefix(typeCast.getExpr())));
    }

    @Override
    public List<AstTransform> visitMemberReference(Tr.MemberReference memberRef) {
        return maybeTransform(memberRef,
                tokens.contains(METHOD_REF) && whitespaceInPrefix(memberRef.getReference()),
                super::visitMemberReference,
                mr -> mr.withReference(stripPrefix(memberRef.getReference())));
    }

    @Override
    public List<AstTransform> visitMultiVariable(Tr.VariableDecls multiVariable) {
        return maybeTransform(multiVariable,
                tokens.contains(ARRAY_DECLARATOR) && multiVariable.getDimensionsBeforeName().stream().anyMatch(this::whitespaceInPrefix),
                super::visitMultiVariable,
                mv -> mv.withDimensionsBeforeName(mv.getDimensionsBeforeName().stream()
                        .map(Formatting::stripPrefix).collect(toList())));
    }

    @Override
    public List<AstTransform> visitAnnotation(Tr.Annotation annotation) {
        return maybeTransform(annotation,
                tokens.contains(AT) && whitespaceInPrefix(annotation.getAnnotationType()),
                super::visitAnnotation,
                a -> a.withAnnotationType(stripPrefix(a.getAnnotationType())));
    }

    @Override
    public List<AstTransform> visitArrayType(Tr.ArrayType arrayType) {
        return maybeTransform(arrayType,
                tokens.contains(ARRAY_DECLARATOR) && arrayType.getDimensions().stream().anyMatch(this::whitespaceInPrefix),
                super::visitArrayType,
                at -> at.withDimensions(at.getDimensions().stream()
                        .map(Formatting::stripPrefix).collect(toList())));
    }

    @Override
    public List<AstTransform> visitNewArray(Tr.NewArray newArray) {
        return maybeTransform(newArray,
                tokens.contains(ARRAY_INIT) &&
                        Optional.ofNullable(newArray.getInitializer())
                                .map(Tr.NewArray.Initializer::getElements)
                                .map(init -> !init.isEmpty() && (whitespaceInPrefix(init.get(0)) ||
                                        whitespaceInSuffix(init.get(init.size() - 1))))
                                .orElse(false),
                super::visitNewArray,
                na -> {
                    @SuppressWarnings("ConstantConditions") List<Expression> fixedInit =
                            new ArrayList<>(na.getInitializer().getElements());

                    if (fixedInit.size() == 1) {
                        fixedInit.set(0, fixedInit.get(0).withFormatting(EMPTY));
                    } else {
                        fixedInit.set(0, stripPrefix(fixedInit.get(0)));
                        fixedInit.set(fixedInit.size() - 1, stripSuffix(fixedInit.get(fixedInit.size() - 1)));
                    }

                    return na.withInitializer(na.getInitializer().withElements(fixedInit));
                }
        );
    }

    @Override
    public List<AstTransform> visitArrayAccess(Tr.ArrayAccess arrayAccess) {
        return maybeTransform(arrayAccess,
                tokens.contains(INDEX_OP) && whitespaceInPrefix(arrayAccess.getDimension()),
                super::visitArrayAccess,
                aa -> aa.withDimension(stripPrefix(aa.getDimension())));
    }

    @Override
    public List<AstTransform> visitUnary(Tr.Unary unary) {
        Tr.Unary.Operator op = unary.getOperator();
        if (op instanceof Tr.Unary.Operator.PreDecrement ||
                op instanceof Tr.Unary.Operator.PreIncrement ||
                op instanceof Tr.Unary.Operator.Negative ||
                op instanceof Tr.Unary.Operator.Positive ||
                op instanceof Tr.Unary.Operator.Complement ||
                op instanceof Tr.Unary.Operator.Not) {

            return maybeTransform(unary,
                    (tokens.contains(DEC) ||
                            tokens.contains(INC) ||
                            tokens.contains(BNOT) ||
                            tokens.contains(LNOT) ||
                            tokens.contains(UNARY_PLUS) ||
                            tokens.contains(UNARY_MINUS)) && whitespaceInPrefix(unary.getExpr()),
                    super::visitUnary,
                    u -> u.withExpr(stripPrefix(u.getExpr()))
            );
        }

        return super.visitUnary(unary);
    }

    @Override
    public List<AstTransform> visitFieldAccess(Tr.FieldAccess fieldAccess) {
        return maybeTransform(fieldAccess,
                tokens.contains(DOT) && whitespaceInPrefix(fieldAccess.getName()),
                super::visitFieldAccess,
                fa -> fa.withName(stripPrefix(fa.getName())));
    }

    @Override
    public List<AstTransform> visitMethodInvocation(Tr.MethodInvocation method) {
        return maybeTransform(method,
                tokens.contains(DOT) && whitespaceInPrefix(method.getName()),
                super::visitMethodInvocation,
                m -> m.withName(stripPrefix(m.getName())));
    }

    private boolean whitespaceInSuffix(@Nullable Tree t) {
        return t != null && (t.getFormatting().getSuffix().contains(" ") || t.getFormatting().getSuffix().contains("\t"));
    }

    private boolean whitespaceInPrefix(@Nullable Tree t) {
        if (t == null) {
            return false;
        }
        String prefix = t.getFormatting().getPrefix();
        return (prefix.contains(" ") || prefix.contains("\t")) && (!allowLineBreaks || !prefix.startsWith("\n"));
    }
}
