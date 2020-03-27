package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.Tree;
import org.openrewrite.java.refactor.JavaFormatter;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.gradle.rewrite.checkstyle.policy.Token.*;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

@Builder
public class NeedBraces extends JavaRefactorVisitor {
    @Builder.Default
    private final boolean allowSingleLineStatement = false;

    @Builder.Default
    private final boolean allowEmptyLoopBody = false;

    @Builder.Default
    private final Set<Token> tokens = Set.of(
            LITERAL_DO, LITERAL_ELSE, LITERAL_FOR, LITERAL_IF, LITERAL_WHILE
    );

    @Override
    public String getName() {
        return "checkstyle.NeedBraces";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitIf(J.If iff) {
        J.If i = refactor(iff, super::visitIf);

        if(tokens.contains(LITERAL_IF) &&
                !(iff.getThenPart() instanceof J.Block) &&
                isNotAllowableSingleLine()) {
            i = i.withThenPart(addBraces(i.getThenPart()));
        }

        return i;
    }

    @Override
    public J visitElse(J.If.Else elze) {
        J.If.Else e = refactor(elze, super::visitElse);

        if (tokens.contains(LITERAL_IF) &&
                !(elze.getStatement() instanceof J.Block) &&
                isNotAllowableSingleLine()) {
            e = e.withStatement(addBraces(e.getStatement()));
        }

        return e;
    }

    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop) {
        J.WhileLoop w = refactor(whileLoop, super::visitWhileLoop);

        Statement body = w.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof J.Empty || body instanceof J.Block :
                body instanceof J.Block;

        if(tokens.contains(LITERAL_WHILE) &&
                !hasAllowableBodyType &&
                isNotAllowableSingleLine()) {
            w = w.withBody(addBraces(w.getBody()));
        }

        return w;
    }

    @Override
    public J visitDoWhileLoop(J.DoWhileLoop doWhileLoop) {
        J.DoWhileLoop w = refactor(doWhileLoop, super::visitDoWhileLoop);

        if(tokens.contains(LITERAL_DO) &&
                !(w.getBody() instanceof J.Block) &&
                isNotAllowableSingleLine()) {
            w = w.withBody(addBraces(w.getBody()));
        }

        return w;
    }

    @Override
    public J visitForLoop(J.ForLoop forLoop) {
        J.ForLoop f = refactor(forLoop, super::visitForLoop);

        Statement body = f.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof J.Empty || body instanceof J.Block :
                body instanceof J.Block;

        if(tokens.contains(LITERAL_FOR) &&
                !hasAllowableBodyType &&
                isNotAllowableSingleLine()) {
            f = f.withBody(addBraces(f.getBody()));
        }

        return f;
    }

    private boolean isNotAllowableSingleLine() {
        return !allowSingleLineStatement || new SpansMultipleLines(getCursor().getTree(), null)
                .visit((Tree) getCursor().getTree());
    }

    @SuppressWarnings("ConstantConditions")
    private Statement addBraces(Statement body) {
        if(body instanceof J.Block) {
            return body;
        }

        int enclosingIndent = getCursor().getParentOrThrow().firstEnclosing(J.Block.class).getIndent();
        JavaFormatter.Result format = formatter.findIndent(enclosingIndent, getCursor().getParentOrThrow().getTree());

        String originalBodySuffix = body.getFormatting().getSuffix();

        return new J.Block<>(randomId(),
                null,
                body instanceof J.Empty ?
                        emptyList() :
                        singletonList(body.withFormatting(format(format.getPrefix(1)))),
                format(" ", originalBodySuffix),
                format.getPrefix());
    }
}
