package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.Formatter;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.List;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.format;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

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
        return "NeedBraces";
    }

    @Override
    public List<AstTransform> visitIf(Tr.If iff) {
        return maybeTransform(tokens.contains(LITERAL_IF) &&
                        !(iff.getThenPart() instanceof Tr.Block) &&
                        isNotAllowableSingleLine(),
                super.visitIf(iff),
                transform(iff.getThenPart(), this::addBraces)
        );
    }

    @Override
    public List<AstTransform> visitElse(Tr.If.Else elze) {
        return maybeTransform(tokens.contains(LITERAL_IF) &&
                        !(elze.getStatement() instanceof Tr.Block) &&
                        isNotAllowableSingleLine(),
                super.visitElse(elze),
                transform(elze.getStatement(), this::addBraces)
        );
    }

    @Override
    public List<AstTransform> visitWhileLoop(Tr.WhileLoop whileLoop) {
        Statement body = whileLoop.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof Tr.Empty || body instanceof Tr.Block :
                body instanceof Tr.Block;

        return maybeTransform(tokens.contains(LITERAL_WHILE) &&
                        !hasAllowableBodyType &&
                        isNotAllowableSingleLine(),
                super.visitWhileLoop(whileLoop),
                transform(body, this::addBraces)
        );
    }

    @Override
    public List<AstTransform> visitDoWhileLoop(Tr.DoWhileLoop doWhileLoop) {
        return maybeTransform(tokens.contains(LITERAL_DO) &&
                        !(doWhileLoop.getBody() instanceof Tr.Block) &&
                        isNotAllowableSingleLine(),
                super.visitDoWhileLoop(doWhileLoop),
                transform(doWhileLoop.getBody(), this::addBraces)
        );
    }

    @Override
    public List<AstTransform> visitForLoop(Tr.ForLoop forLoop) {
        Statement body = forLoop.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof Tr.Empty || body instanceof Tr.Block :
                body instanceof Tr.Block;

        return maybeTransform(tokens.contains(LITERAL_FOR) &&
                        !hasAllowableBodyType &&
                        isNotAllowableSingleLine(),
                super.visitForLoop(forLoop),
                transform(body, this::addBraces)
        );
    }

    private boolean isNotAllowableSingleLine() {
        return !allowSingleLineStatement || new SpansMultipleLines().visit((Tree) getCursor().getTree());
    }

    private Tree addBraces(Tree body, Cursor cursor) {
        @SuppressWarnings("ConstantConditions") int enclosingIndent = cursor.getParentOrThrow().enclosingBlock().getIndent();
        Formatter.Result format = formatter().findIndent(enclosingIndent, singletonList(cursor.getParentOrThrow().getTree()));

        String originalBodySuffix = body.getFormatting().getSuffix();

        return new Tr.Block<>(randomId(),
                null,
                body instanceof Tr.Empty ?
                        emptyList() :
                        singletonList(body.withFormatting(format(format.getPrefix(1)))),
                format(" ", originalBodySuffix),
                format.getPrefix());
    }
}
