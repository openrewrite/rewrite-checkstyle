package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.tree.Cursor;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Statement;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.Formatter;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.Set;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.gradle.rewrite.checkstyle.policy.Token.*;
import static org.openrewrite.tree.Formatting.format;
import static org.openrewrite.tree.J.randomId;

@Builder
public class NeedBraces extends RefactorVisitor {
    @Builder.Default
    private final boolean allowSingleLineStatement = false;

    @Builder.Default
    private final boolean allowEmptyLoopBody = false;

    @Builder.Default
    private final Set<Token> tokens = Set.of(
            LITERAL_DO, LITERAL_ELSE, LITERAL_FOR, LITERAL_IF, LITERAL_WHILE
    );

    @Override
    public String getRuleName() {
        return "checkstyle.NeedBraces";
    }

    @Override
    public List<AstTransform> visitIf(J.If iff) {
        return maybeTransform(iff,
                tokens.contains(LITERAL_IF) &&
                        !(iff.getThenPart() instanceof J.Block) &&
                        isNotAllowableSingleLine(),
                super::visitIf,
                J.If::getThenPart,
                this::addBraces);
    }

    @Override
    public List<AstTransform> visitElse(J.If.Else elze) {
        return maybeTransform(elze,
                tokens.contains(LITERAL_IF) &&
                        !(elze.getStatement() instanceof J.Block) &&
                        isNotAllowableSingleLine(),
                super::visitElse,
                J.If.Else::getStatement,
                this::addBraces);
    }

    @Override
    public List<AstTransform> visitWhileLoop(J.WhileLoop whileLoop) {
        Statement body = whileLoop.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof J.Empty || body instanceof J.Block :
                body instanceof J.Block;

        return maybeTransform(whileLoop,
                tokens.contains(LITERAL_WHILE) &&
                        !hasAllowableBodyType &&
                        isNotAllowableSingleLine(),
                super::visitWhileLoop,
                J.WhileLoop::getBody,
                this::addBraces);
    }

    @Override
    public List<AstTransform> visitDoWhileLoop(J.DoWhileLoop doWhileLoop) {
        return maybeTransform(doWhileLoop,
                tokens.contains(LITERAL_DO) &&
                        !(doWhileLoop.getBody() instanceof J.Block) &&
                        isNotAllowableSingleLine(),
                super::visitDoWhileLoop,
                J.DoWhileLoop::getBody,
                this::addBraces);
    }

    @Override
    public List<AstTransform> visitForLoop(J.ForLoop forLoop) {
        Statement body = forLoop.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof J.Empty || body instanceof J.Block :
                body instanceof J.Block;

        return maybeTransform(forLoop,
                tokens.contains(LITERAL_FOR) &&
                        !hasAllowableBodyType &&
                        isNotAllowableSingleLine(),
                super::visitForLoop,
                J.ForLoop::getBody,
                this::addBraces);
    }

    private boolean isNotAllowableSingleLine() {
        return !allowSingleLineStatement || new SpansMultipleLines(null).visit((Tree) getCursor().getTree());
    }

    private Statement addBraces(Statement body, Cursor cursor) {
        int enclosingIndent = cursor.getParentOrThrow().enclosingBlock().getIndent();
        Formatter.Result format = formatter().findIndent(enclosingIndent, cursor.getParentOrThrow().getTree());

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
