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
import org.openrewrite.checkstyle.policy.PunctuationToken;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.stripSuffix;
import static org.openrewrite.checkstyle.check.WhitespaceChecks.stripPrefixUpToLinebreak;
import static org.openrewrite.checkstyle.check.WhitespaceChecks.stripSuffixUpToLinebreak;
import static org.openrewrite.checkstyle.policy.PunctuationToken.*;

public class NoWhitespaceBefore extends CheckstyleRefactorVisitor {
    private static final Set<PunctuationToken> DEFAULT_TOKENS = Set.of(
            COMMA, SEMI, POST_INC, POST_DEC, ELLIPSIS
    );

    /**
     * Only applies to DOT.
     */
    private final boolean allowLineBreaks;

    private final Set<PunctuationToken> tokens;

    public NoWhitespaceBefore(boolean allowLineBreaks, Set<PunctuationToken> tokens) {
        super("checkstyle.NoWhitespaceBefore");
        this.allowLineBreaks = allowLineBreaks;
        this.tokens = tokens;
        setCursoringOn();
    }

    @AutoConfigure
    public static NoWhitespaceBefore configure(Config config) {
        return fromModule(
                config,
                "NoWhitespaceBefore",
                m -> new NoWhitespaceBefore(
                        m.prop("allowLineBreaks", false),
                        m.propAsTokens(PunctuationToken.class, DEFAULT_TOKENS)
                )
        );
    }

    @Override
    public J visitPackage(J.Package pkg) {
        return maybeStripSuffixBefore(pkg, super::visitPackage, SEMI);
    }

    @Override
    public J visitImport(J.Import impoort) {
        return maybeStripSuffixBefore(impoort, super::visitImport, SEMI);
    }

    @Override
    public J visitFieldAccess(J.FieldAccess fieldAccess) {
        J.FieldAccess f = refactor(fieldAccess, super::visitFieldAccess);
        if (tokens.contains(DOT) && whitespaceInSuffix(fieldAccess.getTarget())) {
            f = f.withTarget(stripSuffix(f.getTarget()));
        }
        return f;
    }

    @Override
    public J visitMethod(J.MethodDecl method) {
        if (method.isAbstract()) {
            return maybeStripSuffixBefore(method, super::visitMethod, SEMI);
        }
        return super.visitMethod(method);
    }

    @Override
    public J visitMethodInvocation(J.MethodInvocation method) {
        J.MethodInvocation m = refactor(method, super::visitMethodInvocation);

        if (method.getSelect() != null && tokens.contains(DOT) && whitespaceInSuffix(method.getSelect())) {
            m = m.withSelect(stripSuffix(m.getSelect()));
        }

        if (tokens.contains(COMMA) && method.getArgs().getArgs().stream()
                .anyMatch(arg -> whitespaceInSuffix(arg) && !isLastArgumentInMethodInvocation(arg, method))) {
            m = m.withArgs(m.getArgs().withArgs(m.getArgs().getArgs().stream()
                    .map(arg -> isLastArgumentInMethodInvocation(arg, method) ? arg : stripSuffix(arg))
                    .collect(toList())));
        }

        return m;
    }

    // don't strip spaces before end parentheses in method invocation arguments
    private boolean isLastArgumentInMethodInvocation(Expression arg, J.MethodInvocation parent) {
        return parent.getArgs().getArgs().stream().reduce((r1, r2) -> r2)
                .map(lastArg -> lastArg == arg)
                .orElse(false);
    }

    @Override
    public J visitStatement(Statement statement) {
        Tree parent = getCursor().getParentOrThrow().getTree();
        if (!(parent instanceof J.MethodInvocation) &&
                !(parent instanceof J.FieldAccess) &&
                !(parent instanceof J.ForEachLoop) && // don't strip spaces before ':' in for each loop
                !(parent instanceof J.Try) && // don't strip suffix of variable declarations in try-with-resources statements
                !isLastArgumentInMethodDeclaration(statement, parent) &&
                !isStatementPrecedingTernaryConditionOrFalse(statement, parent) &&
                !isStatementPrecedingInstanceof(statement, parent) &&
                statement.isSemicolonTerminated()) {
            return maybeStripSuffixBefore(statement, super::visitStatement, SEMI);
        }
        return super.visitStatement(statement);
    }

    // don't strip spaces before end parentheses in method declaration arguments
    private boolean isLastArgumentInMethodDeclaration(Statement statement, Tree parent) {
        if (!(parent instanceof J.MethodDecl)) {
            return false;
        }

        return ((J.MethodDecl) parent).getParams().getParams().stream().reduce((r1, r2) -> r2)
                .map(lastArg -> lastArg == statement)
                .orElse(false);
    }

    private boolean isStatementPrecedingTernaryConditionOrFalse(Statement statement, Tree parent) {
        return parent instanceof J.Ternary && (((J.Ternary) parent).getCondition() == statement ||
                ((J.Ternary) parent).getTruePart() == statement);
    }

    private boolean isStatementPrecedingInstanceof(Statement statement, Tree parent) {
        return parent instanceof J.InstanceOf && ((J.InstanceOf) parent).getExpr() == statement;
    }

    @Override
    public J visitForLoop(J.ForLoop forLoop) {
        J.ForLoop f = refactor(forLoop, super::visitForLoop);

        J.ForLoop.Control ctrl = forLoop.getControl();
        if ((tokens.contains(SEMI) && forLoop.getBody() instanceof J.Empty && whitespaceInPrefix(forLoop.getBody())) ||
                (tokens.contains(SEMI) && (whitespaceInSuffix(ctrl.getInit()) ||
                        whitespaceInSuffix(ctrl.getCondition()) ||
                        whitespaceInSuffix(ctrl.getUpdate().get(ctrl.getUpdate().size() - 1)))
                ) || (tokens.contains(COMMA) && ctrl.getUpdate().stream().anyMatch(this::whitespaceInSuffix))) {
            J.ForLoop.Control fixCtrl = f.getControl();
            List<Statement> fixUpdate = new ArrayList<>(fixCtrl.getUpdate());

            for (int i = 0; i < fixUpdate.size(); i++) {
                Statement update = fixUpdate.get(i);
                if (tokens.contains(COMMA) && i != fixUpdate.size() - 1) {
                    fixUpdate.set(i, stripSuffixUpToLinebreak(update));
                } else if (tokens.contains(SEMI) && i == fixUpdate.size() - 1) {
                    fixUpdate.set(i, stripSuffixUpToLinebreak(update));
                }
            }

            // commas between init variables will be stripped by the VariableDecls visitor separately
            fixCtrl = fixCtrl.withInit(stripSuffixUpToLinebreak(fixCtrl.getInit()))
                    .withCondition(stripSuffixUpToLinebreak(fixCtrl.getCondition()))
                    .withUpdate(fixUpdate);

            f = f.withControl(fixCtrl)
                    .withBody(f.getBody() instanceof J.Empty ? stripPrefixUpToLinebreak(f.getBody()) : f.getBody());
        }

        return f;
    }

    @Override
    public J visitMultiVariable(J.VariableDecls multiVariable) {
        J.VariableDecls m = refactor(multiVariable, super::visitMultiVariable);
        if (tokens.contains(ELLIPSIS) && whitespaceInPrefix(multiVariable.getVarargs())) {
            m = m.withVarargs(stripPrefixUpToLinebreak(m.getVarargs()));
        }
        return m;
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        return maybeStripSuffixBefore(variable, super::visitVariable, COMMA);
    }

    @Override
    public J visitUnary(J.Unary unary) {
        J.Unary u = refactor(unary, super::visitUnary);
        if (whitespaceInPrefix(unary.getOperator()) &&
                (unary.getOperator() instanceof J.Unary.Operator.PostDecrement ||
                        unary.getOperator() instanceof J.Unary.Operator.PostIncrement) &&
                (tokens.contains(POST_DEC) || tokens.contains(POST_INC))) {
            u = u.withOperator(stripPrefixUpToLinebreak(u.getOperator()));
        }
        return u;
    }

    @Override
    public J visitTypeParameters(J.TypeParameters typeParams) {
        return maybeStripPrefixBefore(typeParams, super::visitTypeParameters, GENERIC_START);
    }

    @Override
    public J visitTypeParameter(J.TypeParameter typeParam) {
        J.TypeParameters typeParams = getCursor().getParentOrThrow().getTree();
        if (typeParams.getParams().get(typeParams.getParams().size() - 1) == typeParam) {
            return maybeStripSuffixBefore(typeParam, super::visitTypeParameter, GENERIC_END);
        }
        return super.visitTypeParameter(typeParam);
    }

    @Override
    public J visitMemberReference(J.MemberReference memberRef) {
        J.MemberReference m = refactor(memberRef, super::visitMemberReference);
        if (tokens.contains(METHOD_REF) && whitespaceInSuffix(memberRef.getContaining())) {
            m = m.withContaining(stripSuffixUpToLinebreak(m.getContaining()));
        }
        return m;
    }

    @Override
    public J visitForEachLoop(J.ForEachLoop forEachLoop) {
        J.ForEachLoop f = refactor(forEachLoop, super::visitForEachLoop);
        if (tokens.contains(SEMI) && forEachLoop.getBody() instanceof J.Empty && whitespaceInPrefix(forEachLoop.getBody())) {
            f = f.withBody(stripPrefixUpToLinebreak(f.getBody()));
        }
        return f;
    }

    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop) {
        J.WhileLoop w = refactor(whileLoop, super::visitWhileLoop);
        if (tokens.contains(SEMI) && whileLoop.getBody() instanceof J.Empty && whitespaceInPrefix(whileLoop.getBody())) {
            w = w.withBody(stripPrefixUpToLinebreak(w.getBody()));
        }
        return w;
    }

    private <T extends J> T maybeStripSuffixBefore(T tree,
                                                   Function<T, Tree> callSuper,
                                                   PunctuationToken... tokensToMatch) {
        T t = refactor(tree, callSuper);
        if (stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInSuffix(tree)) {
            t = stripSuffixUpToLinebreak(t);
        }
        return t;
    }

    private <T extends J> T maybeStripPrefixBefore(T tree,
                                                   Function<T, Tree> callSuper,
                                                   PunctuationToken... tokensToMatch) {
        T t = refactor(tree, callSuper);
        if (stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInPrefix(tree)) {
            t = stripPrefixUpToLinebreak(t);
        }
        return t;
    }

    private boolean whitespaceInSuffix(@Nullable Tree t) {
        if (t == null) {
            return false;
        }
        String suffix = t.getFormatting().getSuffix();
        return (suffix.contains(" ") || suffix.contains("\t")) && (!allowLineBreaks || !suffix.startsWith("\n"));
    }

    private boolean whitespaceInPrefix(@Nullable Tree t) {
        return t != null && (t.getFormatting().getPrefix().contains(" ") || t.getFormatting().getPrefix().contains("\t"));
    }
}
