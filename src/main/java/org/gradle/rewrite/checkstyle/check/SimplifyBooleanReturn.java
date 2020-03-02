package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.java.refactor.DeleteStatement;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

public class SimplifyBooleanReturn extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.SimplifyBooleanReturn";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitIf(J.If iff) {
        J.If i = refactor(iff, super::visitIf);

        if (thenHasOnlyReturnStatement(iff) && singleFollowingStatement()
                .flatMap(stat -> Optional.ofNullable(stat instanceof J.Return ? (J.Return) stat : null))
                .map(J.Return::getExpr)
                .map(r -> isLiteralFalse(r) || isLiteralTrue(r))
                .orElse(true)) {
            J.Return retrn = getReturnIfOnlyStatementInThen(iff).orElseThrow();
            Expression ifCondition = i.getIfCondition().getTree();

            if (isLiteralTrue(retrn.getExpr()) && getReturnExprIfOnlyStatementInElseThen(i)
                    .map(this::isLiteralFalse).orElse(true)) {
                if (i.getElsePart() != null) {
                    andThen(new DeleteStatement(i.getElsePart().getStatement()));
                }

                singleFollowingStatement()
                        .ifPresent(s -> andThen(new DeleteStatement(s)));

                return retrn
                        .withExpr(ifCondition.withFormatting(format(" ")))
                        .withFormatting(i.getFormatting());
            } else if (isLiteralFalse(retrn.getExpr()) && getReturnExprIfOnlyStatementInElseThen(i)
                    .map(this::isLiteralTrue).orElse(true)) {
                if (i.getElsePart() != null) {
                    andThen(new DeleteStatement(i.getElsePart().getStatement()));
                }

                singleFollowingStatement().ifPresent(s -> andThen(new DeleteStatement(s)));

                //  we need to NOT the expression inside the if condition

                var maybeParenthesizedCondition = ifCondition instanceof J.Binary ?
                        new J.Parentheses<>(randomId(), ifCondition, EMPTY) :
                        ifCondition;

                return retrn
                        .withExpr(new J.Unary(randomId(), new J.Unary.Operator.Not(randomId(), EMPTY),
                                maybeParenthesizedCondition, JavaType.Primitive.Boolean, format(" ")))
                        .withFormatting(i.getFormatting());
            }
        }

        return i;
    }

    private boolean thenHasOnlyReturnStatement(J.If iff) {
        return getReturnIfOnlyStatementInThen(iff)
                .map(retrn -> isLiteralFalse(retrn.getExpr()) || isLiteralTrue(retrn.getExpr()))
                .orElse(false);
    }

    private Optional<Statement> singleFollowingStatement() {
        J.Block<Statement> block = getCursor().getParentOrThrow().getTree();
        List<Statement> following = block.getStatements().stream().dropWhile(s -> s != getCursor().getTree())
                .skip(1).collect(toList());
        return Optional.ofNullable(following.size() != 1 ? null : following.get(0));
    }

    private boolean isLiteralTrue(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    private boolean isLiteralFalse(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() == Boolean.valueOf(false);
    }

    private Optional<J.Return> getReturnIfOnlyStatementInThen(J.If iff) {
        if (iff.getThenPart() instanceof J.Return) {
            return Optional.of((J.Return) iff.getThenPart());
        }
        if (iff.getThenPart() instanceof J.Block) {
            J.Block<?> then = (J.Block<?>) iff.getThenPart();
            if (then.getStatements().size() == 1 && then.getStatements().get(0) instanceof J.Return) {
                return Optional.of((J.Return) then.getStatements().get(0));
            }
        }
        return Optional.empty();
    }

    private Optional<Expression> getReturnExprIfOnlyStatementInElseThen(J.If iff2) {
        return ofNullable(iff2.getElsePart())
                .flatMap(elze -> ofNullable(elze.getStatement() instanceof J.If ? (J.If) elze.getStatement() : null))
                .flatMap(this::getReturnIfOnlyStatementInThen)
                .map(J.Return::getExpr);
    }
}
