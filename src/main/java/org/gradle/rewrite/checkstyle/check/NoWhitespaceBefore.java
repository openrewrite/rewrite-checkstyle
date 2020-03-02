package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.PunctuationToken;
import org.openrewrite.Formatting;
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.PunctuationToken.*;
import static org.openrewrite.Formatting.stripPrefix;
import static org.openrewrite.Formatting.stripSuffix;

@Builder
public class NoWhitespaceBefore extends JavaRefactorVisitor {
    /**
     * Only applies to DOT.
     */
    @Builder.Default
    private final boolean allowLineBreaks = false;

    @Builder.Default
    private final Set<PunctuationToken> tokens = Set.of(
            COMMA, SEMI, POST_INC, POST_DEC, ELLIPSIS
    );

    @Override
    public String getName() {
        return "checkstyle.NoWhitespaceBefore";
    }

    @Override
    public boolean isCursored() {
        return true;
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
        if ((method.getSelect() != null && tokens.contains(DOT) && whitespaceInSuffix(method.getSelect())) ||
                (tokens.contains(COMMA) && method.getArgs().getArgs().stream().anyMatch(this::whitespaceInSuffix))) {
            m = m.withSelect(stripSuffix(m.getSelect()))
                    .withArgs(m.getArgs().withArgs(m.getArgs().getArgs().stream()
                            .map(Formatting::stripSuffix)
                            .collect(toList())));
        }
        return m;
    }

    @Override
    public J visitStatement(Statement statement) {
        Tree parent = getCursor().getParentOrThrow().getTree();
        if (!(parent instanceof J.MethodInvocation) &&
                !(parent instanceof J.FieldAccess) &&
                !(parent instanceof J.ForEachLoop) && // don't strip spaces before ':' in for each loop
                statement.isSemicolonTerminated()) {
            return maybeStripSuffixBefore(statement, super::visitStatement, SEMI);
        }
        return super.visitStatement(statement);
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
                    fixUpdate.set(i, stripSuffix(update));
                } else if (tokens.contains(SEMI) && i == fixUpdate.size() - 1) {
                    fixUpdate.set(i, stripSuffix(update));
                }
            }

            // commas between init variables will be stripped by the VariableDecls visitor separately
            fixCtrl = fixCtrl.withInit(stripSuffix(fixCtrl.getInit()))
                    .withCondition(stripSuffix(fixCtrl.getCondition()))
                    .withUpdate(fixUpdate);

            f = f.withControl(fixCtrl)
                    .withBody(f.getBody() instanceof J.Empty ? stripPrefix(f.getBody()) : f.getBody());
        }

        return f;
    }

    @Override
    public J visitMultiVariable(J.VariableDecls multiVariable) {
        J.VariableDecls m = refactor(multiVariable, super::visitMultiVariable);
        if (tokens.contains(ELLIPSIS) && whitespaceInPrefix(multiVariable.getVarargs())) {
            m = m.withVarargs(stripPrefix(m.getVarargs()));
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
        if(whitespaceInPrefix(unary.getOperator()) &&
                (unary.getOperator() instanceof J.Unary.Operator.PostDecrement ||
                        unary.getOperator() instanceof J.Unary.Operator.PostIncrement) &&
                (tokens.contains(POST_DEC) || tokens.contains(POST_INC))) {
            u = u.withOperator(stripPrefix(u.getOperator()));
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
        if(tokens.contains(METHOD_REF) && whitespaceInSuffix(memberRef.getContaining())) {
            m = m.withContaining(stripSuffix(m.getContaining()));
        }
        return m;
    }

    @Override
    public J visitForEachLoop(J.ForEachLoop forEachLoop) {
        J.ForEachLoop f = refactor(forEachLoop, super::visitForEachLoop);
        if(tokens.contains(SEMI) && forEachLoop.getBody() instanceof J.Empty && whitespaceInPrefix(forEachLoop.getBody())) {
            f = f.withBody(stripPrefix(f.getBody()));
        }
        return f;
    }

    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop) {
        J.WhileLoop w = refactor(whileLoop, super::visitWhileLoop);
        if(tokens.contains(SEMI) && whileLoop.getBody() instanceof J.Empty && whitespaceInPrefix(whileLoop.getBody())) {
            w = w.withBody(stripPrefix(w.getBody()));
        }
        return w;
    }

    private <T extends J> T maybeStripSuffixBefore(T tree,
                                                   Function<T, Tree> callSuper,
                                                   PunctuationToken... tokensToMatch) {
        T t = refactor(tree, callSuper);
        if (stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInSuffix(tree)) {
            t = stripSuffix(t);
        }
        return t;
    }

    private <T extends J> T maybeStripPrefixBefore(T tree,
                                                   Function<T, Tree> callSuper,
                                                   PunctuationToken... tokensToMatch) {
        T t = refactor(tree, callSuper);
        if (stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInPrefix(tree)) {
            t = stripPrefix(t);
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
