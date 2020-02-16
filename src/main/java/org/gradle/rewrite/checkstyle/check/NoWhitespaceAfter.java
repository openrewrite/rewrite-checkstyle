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
import java.util.Set;

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
        return maybeTransform(tokens.contains(TYPECAST) && whitespaceInPrefix(typeCast.getExpr()),
                super.visitTypeCast(typeCast),
                transform(typeCast, tc -> tc.withExpr(stripPrefix(typeCast.getExpr())))
        );
    }

    @Override
    public List<AstTransform> visitMemberReference(Tr.MemberReference memberRef) {
        return maybeTransform(tokens.contains(METHOD_REF) && whitespaceInPrefix(memberRef.getReference()),
                super.visitMemberReference(memberRef),
                transform(memberRef, mr -> mr.withReference(stripPrefix(memberRef.getReference())))
        );
    }

    @Override
    public List<AstTransform> visitMultiVariable(Tr.VariableDecls multiVariable) {
        return maybeTransform(tokens.contains(ARRAY_DECLARATOR) && multiVariable.getDimensionsBeforeName().stream().anyMatch(this::whitespaceInPrefix),
                super.visitMultiVariable(multiVariable),
                transform(multiVariable, mv -> mv.withDimensionsBeforeName(mv.getDimensionsBeforeName().stream()
                        .map(this::stripPrefix).collect(toList())))
        );
    }

    @Override
    public List<AstTransform> visitAnnotation(Tr.Annotation annotation) {
        return maybeTransform(tokens.contains(AT) && whitespaceInPrefix(annotation.getAnnotationType()),
                super.visitAnnotation(annotation),
                transform(annotation, a -> a.withAnnotationType(stripPrefix(a.getAnnotationType())))
        );
    }

    @Override
    public List<AstTransform> visitArrayType(Tr.ArrayType arrayType) {
        return maybeTransform(tokens.contains(ARRAY_DECLARATOR) && arrayType.getDimensions().stream().anyMatch(this::whitespaceInPrefix),
                super.visitArrayType(arrayType),
                transform(arrayType, at -> at.withDimensions(at.getDimensions().stream()
                        .map(this::stripPrefix).collect(toList())))
        );
    }

    @Override
    public List<AstTransform> visitNewArray(Tr.NewArray newArray) {
        if (newArray.getInitializer() == null) {
            return super.visitNewArray(newArray);
        }

        List<Expression> init = newArray.getInitializer().getElements();

        return maybeTransform(tokens.contains(ARRAY_INIT) && !init.isEmpty() &&
                        (whitespaceInPrefix(init.get(0)) || whitespaceInSuffix(init.get(init.size() - 1))),
                super.visitNewArray(newArray),
                transform(newArray, na -> {
                    @SuppressWarnings("ConstantConditions") List<Expression> fixedInit =
                            new ArrayList<>(na.getInitializer().getElements());

                    if (fixedInit.size() == 1) {
                        fixedInit.set(0, fixedInit.get(0).withFormatting(Formatting.EMPTY));
                    } else {
                        fixedInit.set(0, stripPrefix(fixedInit.get(0)));
                        fixedInit.set(fixedInit.size() - 1, stripSuffix(fixedInit.get(fixedInit.size() - 1)));
                    }

                    return na.withInitializer(na.getInitializer().withElements(fixedInit));
                })
        );
    }

    @Override
    public List<AstTransform> visitArrayAccess(Tr.ArrayAccess arrayAccess) {
        return maybeTransform(tokens.contains(INDEX_OP) && whitespaceInPrefix(arrayAccess.getDimension()),
                super.visitArrayAccess(arrayAccess),
                transform(arrayAccess, aa -> aa.withDimension(stripPrefix(aa.getDimension())))
        );
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
            return maybeTransform((tokens.contains(DEC) ||
                            tokens.contains(INC) ||
                            tokens.contains(BNOT) ||
                            tokens.contains(LNOT) ||
                            tokens.contains(UNARY_PLUS) ||
                            tokens.contains(UNARY_MINUS)) && whitespaceInPrefix(unary.getExpr()),
                    super.visitUnary(unary),
                    transform(unary, u -> u.withExpr(stripPrefix(u.getExpr())))
            );
        }

        return super.visitUnary(unary);
    }

    @Override
    public List<AstTransform> visitFieldAccess(Tr.FieldAccess fieldAccess) {
        return maybeTransform(tokens.contains(DOT) && whitespaceInPrefix(fieldAccess.getName()),
                super.visitFieldAccess(fieldAccess),
                transform(fieldAccess, fa -> fa.withName(stripPrefix(fa.getName())))
        );
    }

    @Override
    public List<AstTransform> visitMethodInvocation(Tr.MethodInvocation method) {
        return maybeTransform(tokens.contains(DOT) && whitespaceInPrefix(method.getName()),
                super.visitMethodInvocation(method),
                transform(method, m -> m.withName(stripPrefix(m.getName())))
        );
    }

    private boolean whitespaceInSuffix(@Nullable Tree t) {
        return t != null && (t.getFormatting().getSuffix().contains(" ") || t.getFormatting().getSuffix().contains("\t"));
    }

    private boolean whitespaceInPrefix(@Nullable Tree t) {
        if(t == null) {
            return false;
        }
        String prefix = t.getFormatting().getPrefix();
        return (prefix.contains(" ") || prefix.contains("\t")) && (!allowLineBreaks || !prefix.startsWith("\n"));
    }

    private <T extends Tree> T stripSuffix(@Nullable T t) {
        return t == null ? null : t.withFormatting(t.getFormatting().withSuffix(""));
    }

    private <T extends Tree> T stripPrefix(@Nullable T t) {
        return t == null ? null : t.withFormatting(t.getFormatting().withPrefix(""));
    }
}
