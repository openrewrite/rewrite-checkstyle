package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.internal.lang.Nullable;
import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.PunctuationToken;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.netflix.rewrite.tree.Formatting.stripPrefix;
import static com.netflix.rewrite.tree.Formatting.stripSuffix;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.PunctuationToken.*;

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
        return "NoWhitespaceBefore";
    }

    @Override
    public List<AstTransform> visitPackage(Tr.Package pkg) {
        return maybeStripSuffixBefore(pkg, super::visitPackage, SEMI);
    }

    @Override
    public List<AstTransform> visitImport(Tr.Import impoort) {
        return maybeStripSuffixBefore(impoort, super::visitImport, SEMI);
    }

    @Override
    public List<AstTransform> visitFieldAccess(Tr.FieldAccess fieldAccess) {
        return maybeTransform(tokens.contains(DOT) && whitespaceInSuffix(fieldAccess.getTarget()),
                super.visitFieldAccess(fieldAccess),
                transform(fieldAccess, fa -> fa.withTarget(stripSuffix(fa.getTarget())))
        );
    }

    @Override
    public List<AstTransform> visitMethod(Tr.MethodDecl method) {
        if (method.isAbstract()) {
            return maybeStripSuffixBefore(method, super::visitMethod, SEMI);
        }
        return super.visitMethod(method);
    }

    @Override
    public List<AstTransform> visitMethodInvocation(Tr.MethodInvocation method) {
        return maybeTransform((method.getSelect() != null && tokens.contains(DOT) && whitespaceInSuffix(method.getSelect())) ||
                        (tokens.contains(COMMA) && method.getArgs().getArgs().stream().anyMatch(this::whitespaceInSuffix)),
                super.visitMethodInvocation(method),
                transform(method, m -> m.withSelect(stripSuffix(m.getSelect()))
                        .withArgs(m.getArgs().withArgs(m.getArgs().getArgs().stream()
                                .map(Formatting::stripSuffix)
                                .collect(toList()))))
        );
    }

    @Override
    public List<AstTransform> visitStatement(Statement statement) {
        Tree parent = getCursor().getParentOrThrow().getTree();
        if (!(parent instanceof Tr.MethodInvocation) &&
                !(parent instanceof Tr.FieldAccess) &&
                !(parent instanceof Tr.ForEachLoop) && // don't strip spaces before ':' in for each loop
                statement.isSemicolonTerminated()) {
            return maybeStripSuffixBefore(statement, super::visitStatement, SEMI);
        }
        return super.visitStatement(statement);
    }

    @Override
    public List<AstTransform> visitForLoop(Tr.ForLoop forLoop) {
        Tr.ForLoop.Control ctrl = forLoop.getControl();
        return maybeTransform(
                (tokens.contains(SEMI) && forLoop.getBody() instanceof Tr.Empty && whitespaceInPrefix(forLoop.getBody())) ||
                        (tokens.contains(SEMI) && (whitespaceInSuffix(ctrl.getInit()) ||
                                whitespaceInSuffix(ctrl.getCondition()) ||
                                whitespaceInSuffix(ctrl.getUpdate().get(ctrl.getUpdate().size() - 1)))
                        ) ||
                        (tokens.contains(COMMA) && ctrl.getUpdate().stream().anyMatch(this::whitespaceInSuffix)),
                super.visitForLoop(forLoop),
                transform(forLoop, f -> {
                    Tr.ForLoop.Control fixCtrl = f.getControl();
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
                            .withBody(f.getBody() instanceof Tr.Empty ? stripPrefix(f.getBody()) : f.getBody());
                })
        );
    }

    @Override
    public List<AstTransform> visitMultiVariable(Tr.VariableDecls multiVariable) {
        return maybeTransform(tokens.contains(ELLIPSIS) && whitespaceInPrefix(multiVariable.getVarargs()),
                super.visitMultiVariable(multiVariable),
                transform(multiVariable, mv -> mv.withVarargs(stripPrefix(mv.getVarargs())))
        );
    }

    @Override
    public List<AstTransform> visitVariable(Tr.VariableDecls.NamedVar variable) {
        return maybeStripSuffixBefore(variable, super::visitVariable, COMMA);
    }

    @Override
    public List<AstTransform> visitUnary(Tr.Unary unary) {
        if (unary.getOperator() instanceof Tr.Unary.Operator.PostDecrement || unary.getOperator() instanceof Tr.Unary.Operator.PostIncrement) {
            return maybeTransform((tokens.contains(POST_DEC) || tokens.contains(POST_INC)) && whitespaceInPrefix(unary.getOperator()),
                    super.visitUnary(unary),
                    transform(unary, u -> u.withOperator(stripPrefix(u.getOperator())))
            );
        }

        return super.visitUnary(unary);
    }

    @Override
    public List<AstTransform> visitTypeParameters(Tr.TypeParameters typeParams) {
        return maybeStripPrefixBefore(typeParams, super::visitTypeParameters, GENERIC_START);
    }

    @Override
    public List<AstTransform> visitTypeParameter(Tr.TypeParameter typeParam) {
        Tr.TypeParameters typeParams = getCursor().getParentOrThrow().getTree();
        if (typeParams.getParams().get(typeParams.getParams().size() - 1) == typeParam) {
            return maybeStripSuffixBefore(typeParam, super::visitTypeParameter, GENERIC_END);
        }
        return super.visitTypeParameter(typeParam);
    }

    @Override
    public List<AstTransform> visitMemberReference(Tr.MemberReference memberRef) {
        return maybeTransform(tokens.contains(METHOD_REF) && whitespaceInSuffix(memberRef.getContaining()),
                super.visitMemberReference(memberRef),
                transform(memberRef, mr -> mr.withContaining(stripSuffix(mr.getContaining())))
        );
    }

    @Override
    public List<AstTransform> visitForEachLoop(Tr.ForEachLoop forEachLoop) {
        return maybeTransform(tokens.contains(SEMI) && forEachLoop.getBody() instanceof Tr.Empty && whitespaceInPrefix(forEachLoop.getBody()),
                super.visitForEachLoop(forEachLoop),
                transform(forEachLoop, w -> w.withBody(stripPrefix(w.getBody())))
        );
    }

    @Override
    public List<AstTransform> visitWhileLoop(Tr.WhileLoop whileLoop) {
        return maybeTransform(tokens.contains(SEMI) && whileLoop.getBody() instanceof Tr.Empty && whitespaceInPrefix(whileLoop.getBody()),
                super.visitWhileLoop(whileLoop),
                transform(whileLoop, w -> w.withBody(stripPrefix(w.getBody())))
        );
    }

    private <T extends Tree> List<AstTransform> maybeStripSuffixBefore(T tree, Function<T, List<AstTransform>> callSuper,
                                                                       PunctuationToken... tokensToMatch) {
        return maybeTransform(stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInSuffix(tree),
                callSuper.apply(tree),
                transform(tree, Formatting::stripSuffix)
        );
    }

    private <T extends Tree> List<AstTransform> maybeStripPrefixBefore(T tree, Function<T, List<AstTransform>> callSuper,
                                                                       PunctuationToken... tokensToMatch) {
        return maybeTransform(stream(tokensToMatch).anyMatch(tokens::contains) && whitespaceInPrefix(tree),
                callSuper.apply(tree),
                transform(tree, Formatting::stripPrefix)
        );
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
