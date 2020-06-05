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
package org.openrewrite.checkstyle;

import org.openrewrite.Tree;
import org.openrewrite.checkstyle.policy.PunctuationToken;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.stripPrefix;

@AutoConfigure
public class NoWhitespaceAfter extends CheckstyleRefactorVisitor {
    private static final Set<PunctuationToken> DEFAULT_TOKENS = Set.of(
            PunctuationToken.ARRAY_INIT, PunctuationToken.AT, PunctuationToken.INC, PunctuationToken.DEC, PunctuationToken.UNARY_MINUS, PunctuationToken.UNARY_PLUS, PunctuationToken.BNOT, PunctuationToken.LNOT, PunctuationToken.DOT, PunctuationToken.ARRAY_DECLARATOR, PunctuationToken.INDEX_OP
    );

    /**
     * Only applies to DOT.
     */
    private boolean allowLineBreaks;

    private Set<PunctuationToken> tokens;

    public NoWhitespaceAfter() {
        setCursoringOn();
    }

    @Override
    protected void configure(Module m) {
        this.allowLineBreaks = m.prop("allowLineBreaks", true);
        this.tokens = m.propAsTokens(PunctuationToken.class, DEFAULT_TOKENS);
    }

    @Override
    public J visitTypeCast(J.TypeCast typeCast) {
        J.TypeCast t = refactor(typeCast, super::visitTypeCast);
        if (tokens.contains(PunctuationToken.TYPECAST) && WhitespaceChecks.prefixStartsWithNonLinebreakWhitespace(typeCast.getExpr())) {
            t = t.withExpr(WhitespaceChecks.stripPrefixUpToLinebreak(typeCast.getExpr()));
        }
        return t;
    }

    @Override
    public J visitMemberReference(J.MemberReference memberRef) {
        J.MemberReference m = refactor(memberRef, super::visitMemberReference);
        if (tokens.contains(PunctuationToken.METHOD_REF) && WhitespaceChecks.prefixStartsWithNonLinebreakWhitespace(memberRef.getReference())) {
            m = m.withReference(WhitespaceChecks.stripPrefixUpToLinebreak(memberRef.getReference()));
        }
        return m;
    }

    @Override
    public J visitMultiVariable(J.VariableDecls multiVariable) {
        J.VariableDecls m = refactor(multiVariable, super::visitMultiVariable);
        if (tokens.contains(PunctuationToken.ARRAY_DECLARATOR) && multiVariable.getDimensionsBeforeName().stream()
                .anyMatch(WhitespaceChecks::prefixStartsWithNonLinebreakWhitespace)) {
            m = m.withDimensionsBeforeName(m.getDimensionsBeforeName().stream()
                    .map(WhitespaceChecks::stripPrefixUpToLinebreak).collect(toList()));
        }
        return m;
    }

    @Override
    public J visitAnnotation(J.Annotation annotation) {
        J.Annotation a = refactor(annotation, super::visitAnnotation);
        if (tokens.contains(PunctuationToken.AT) && WhitespaceChecks.prefixStartsWithNonLinebreakWhitespace(annotation.getAnnotationType())) {
            a = a.withAnnotationType(WhitespaceChecks.stripPrefixUpToLinebreak(a.getAnnotationType()));
        }
        return a;
    }

    @Override
    public J visitArrayType(J.ArrayType arrayType) {
        J.ArrayType a = refactor(arrayType, super::visitArrayType);
        if (tokens.contains(PunctuationToken.ARRAY_DECLARATOR) && arrayType.getDimensions().stream()
                .anyMatch(WhitespaceChecks::prefixStartsWithNonLinebreakWhitespace)) {
            a = a.withDimensions(a.getDimensions().stream()
                    .map(WhitespaceChecks::stripPrefixUpToLinebreak).collect(toList()));
        }
        return a;
    }

    @Override
    public J visitNewArray(J.NewArray newArray) {
        J.NewArray n = refactor(newArray, super::visitNewArray);
        if (tokens.contains(PunctuationToken.ARRAY_INIT) &&
                getCursor().firstEnclosing(J.Annotation.class) == null &&
                Optional.ofNullable(newArray.getInitializer())
                        .map(J.NewArray.Initializer::getElements)
                        .map(init -> !init.isEmpty() && (WhitespaceChecks.prefixStartsWithNonLinebreakWhitespace(init.get(0)) ||
                                WhitespaceChecks.suffixStartsWithNonLinebreakWhitespace(init.get(init.size() - 1))))
                        .orElse(false)) {
            List<Expression> fixedInit = new ArrayList<>(n.getInitializer().getElements());

            if (fixedInit.size() == 1) {
                fixedInit.set(0, fixedInit.get(0).withFormatting(EMPTY));
            } else {
                fixedInit.set(0, WhitespaceChecks.stripPrefixUpToLinebreak(fixedInit.get(0)));
                fixedInit.set(fixedInit.size() - 1, WhitespaceChecks.stripSuffixUpToLinebreak(fixedInit.get(fixedInit.size() - 1)));
            }

            n = n.withInitializer(n.getInitializer().withElements(fixedInit));
        }
        return n;
    }

    @Override
    public J visitArrayAccess(J.ArrayAccess arrayAccess) {
        J.ArrayAccess a = refactor(arrayAccess, super::visitArrayAccess);
        if (tokens.contains(PunctuationToken.INDEX_OP) && WhitespaceChecks.prefixStartsWithNonLinebreakWhitespace(arrayAccess.getDimension())) {
            a = a.withDimension(WhitespaceChecks.stripPrefixUpToLinebreak(a.getDimension()));
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

            if ((tokens.contains(PunctuationToken.DEC) ||
                    tokens.contains(PunctuationToken.INC) ||
                    tokens.contains(PunctuationToken.BNOT) ||
                    tokens.contains(PunctuationToken.LNOT) ||
                    tokens.contains(PunctuationToken.UNARY_PLUS) ||
                    tokens.contains(PunctuationToken.UNARY_MINUS)) && WhitespaceChecks.prefixStartsWithNonLinebreakWhitespace(unary.getExpr())) {
                u = u.withExpr(WhitespaceChecks.stripPrefixUpToLinebreak(u.getExpr()));
            }
        }

        return u;
    }

    @Override
    public J visitFieldAccess(J.FieldAccess fieldAccess) {
        J.FieldAccess f = refactor(fieldAccess, super::visitFieldAccess);
        if (tokens.contains(PunctuationToken.DOT) && whitespaceInDotPrefix(fieldAccess.getName())) {
            f = f.withName(allowLineBreaks ?
                    WhitespaceChecks.stripPrefixUpToLinebreak(f.getName()) :
                    stripPrefix(f.getName()));
        }
        return f;
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);
        if (tokens.contains(PunctuationToken.DOT) && whitespaceInDotPrefix(method.getName())) {
            m = m.withName(allowLineBreaks ?
                    WhitespaceChecks.stripPrefixUpToLinebreak(m.getName()) :
                    stripPrefix(m.getName()));
        }
        return m;
    }

    private boolean whitespaceInDotPrefix(@Nullable Tree t) {
        if (t == null) {
            return false;
        } else if (allowLineBreaks) {
            return WhitespaceChecks.prefixStartsWithNonLinebreakWhitespace(t);
        }
        return t.getFormatting().getPrefix().chars().anyMatch(Character::isWhitespace);
    }
}
