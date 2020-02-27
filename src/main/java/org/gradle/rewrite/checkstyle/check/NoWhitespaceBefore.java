package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.PunctuationToken;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.tree.Formatting;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Statement;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.PunctuationToken.*;
import static org.openrewrite.tree.Formatting.stripPrefix;
import static org.openrewrite.tree.Formatting.stripSuffix;

@Builder
public class NoWhitespaceBefore extends RefactorVisitor {
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
    public String getRuleName() {
        return "checkstyle.NoWhitespaceBefore";
    }

    @Override
    public List<AstTransform> visitPackage(J.Package pkg) {
        return maybeStripSuffixBefore(pkg, super::visitPackage, SEMI);
    }

    @Override
    public List<AstTransform> visitImport(J.Import impoort) {
        return maybeStripSuffixBefore(impoort, super::visitImport, SEMI);
    }

    @Override
    public List<AstTransform> visitFieldAccess(J.FieldAccess fieldAccess) {
        return maybeTransform(fieldAccess,
                tokens.contains(DOT) && whitespaceInSuffix(fieldAccess.getTarget()),
                super::visitFieldAccess,
                fa -> fa.withTarget(stripSuffix(fa.getTarget())));
    }

    @Override
    public List<AstTransform> visitMethod(J.MethodDecl method) {
        if (method.isAbstract()) {
            return maybeStripSuffixBefore(method, super::visitMethod, SEMI);
        }
        return super.visitMethod(method);
    }

    @Override
    public List<AstTransform> visitMethodInvocation(J.MethodInvocation method) {
        return maybeTransform(method,
                (method.getSelect() != null && tokens.contains(DOT) && whitespaceInSuffix(method.getSelect())) ||
                        (tokens.contains(COMMA) && method.getArgs().getArgs().stream().anyMatch(this::whitespaceInSuffix)),
                super::visitMethodInvocation,
                m -> m.withSelect(stripSuffix(m.getSelect()))
                        .withArgs(m.getArgs().withArgs(m.getArgs().getArgs().stream()
                                .map(Formatting::stripSuffix)
                                .collect(toList()))));
    }

    @Override
    public List<AstTransform> visitStatement(Statement statement) {
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
    public List<AstTransform> visitForLoop(J.ForLoop forLoop) {
        J.ForLoop.Control ctrl = forLoop.getControl();
        return maybeTransform(forLoop,
                (tokens.contains(SEMI) && forLoop.getBody() instanceof J.Empty && whitespaceInPrefix(forLoop.getBody())) ||
                        (tokens.contains(SEMI) && (whitespaceInSuffix(ctrl.getInit()) ||
                                whitespaceInSuffix(ctrl.getCondition()) ||
                                whitespaceInSuffix(ctrl.getUpdate().get(ctrl.getUpdate().size() - 1)))
                        ) ||
                        (tokens.contains(COMMA) && ctrl.getUpdate().stream().anyMatch(this::whitespaceInSuffix)),
                super::visitForLoop,
                f -> {
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

                    return f.withControl(fixCtrl)
                            .withBody(f.getBody() instanceof J.Empty ? stripPrefix(f.getBody()) : f.getBody());
                }
        );
    }

    @Override
    public List<AstTransform> visitMultiVariable(J.VariableDecls multiVariable) {
        return maybeTransform(multiVariable,
                tokens.contains(ELLIPSIS) && whitespaceInPrefix(multiVariable.getVarargs()),
                super::visitMultiVariable,
                mv -> mv.withVarargs(stripPrefix(mv.getVarargs())));
    }

    @Override
    public List<AstTransform> visitVariable(J.VariableDecls.NamedVar variable) {
        return maybeStripSuffixBefore(variable, super::visitVariable, COMMA);
    }

    @Override
    public List<AstTransform> visitUnary(J.Unary unary) {
        return maybeTransform(unary,
                whitespaceInPrefix(unary.getOperator()) &&
                        (unary.getOperator() instanceof J.Unary.Operator.PostDecrement ||
                                unary.getOperator() instanceof J.Unary.Operator.PostIncrement) &&
                        (tokens.contains(POST_DEC) || tokens.contains(POST_INC)),
                super::visitUnary,
                u -> u.withOperator(stripPrefix(u.getOperator())));
    }

    @Override
    public List<AstTransform> visitTypeParameters(J.TypeParameters typeParams) {
        return maybeStripPrefixBefore(typeParams, super::visitTypeParameters, GENERIC_START);
    }

    @Override
    public List<AstTransform> visitTypeParameter(J.TypeParameter typeParam) {
        J.TypeParameters typeParams = getCursor().getParentOrThrow().getTree();
        if (typeParams.getParams().get(typeParams.getParams().size() - 1) == typeParam) {
            return maybeStripSuffixBefore(typeParam, super::visitTypeParameter, GENERIC_END);
        }
        return super.visitTypeParameter(typeParam);
    }

    @Override
    public List<AstTransform> visitMemberReference(J.MemberReference memberRef) {
        return maybeTransform(memberRef,
                tokens.contains(METHOD_REF) && whitespaceInSuffix(memberRef.getContaining()),
                super::visitMemberReference,
                mr -> mr.withContaining(stripSuffix(mr.getContaining())));
    }

    @Override
    public List<AstTransform> visitForEachLoop(J.ForEachLoop forEachLoop) {
        return maybeTransform(forEachLoop,
                tokens.contains(SEMI) && forEachLoop.getBody() instanceof J.Empty && whitespaceInPrefix(forEachLoop.getBody()),
                super::visitForEachLoop,
                w -> w.withBody(stripPrefix(w.getBody())));
    }

    @Override
    public List<AstTransform> visitWhileLoop(J.WhileLoop whileLoop) {
        return maybeTransform(whileLoop,
                tokens.contains(SEMI) && whileLoop.getBody() instanceof J.Empty && whitespaceInPrefix(whileLoop.getBody()),
                super::visitWhileLoop,
                w -> w.withBody(stripPrefix(w.getBody())));
    }

    private <T extends Tree> List<AstTransform> maybeStripSuffixBefore(T tree, Function<T, List<AstTransform>> callSuper,
                                                                       PunctuationToken... tokensToMatch) {
        return maybeTransform(tree,
                stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInSuffix(tree),
                callSuper,
                Formatting::stripSuffix);
    }

    private <T extends Tree> List<AstTransform> maybeStripPrefixBefore(T tree, Function<T, List<AstTransform>> callSuper,
                                                                       PunctuationToken... tokensToMatch) {
        return maybeTransform(tree,
                stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInPrefix(tree),
                callSuper,
                Formatting::stripPrefix);
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
