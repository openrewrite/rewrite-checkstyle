package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.*;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.Optional;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.tree.Formatting.EMPTY;
import static org.openrewrite.tree.Formatting.format;
import static org.openrewrite.tree.J.randomId;

public class SimplifyBooleanReturn extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.SimplifyBooleanReturn";
    }

    @Override
    public List<AstTransform> visitIf(J.If iff) {
        return maybeTransform(iff,
                thenHasOnlyReturnStatement(iff) && singleFollowingStatement(getCursor())
                        .flatMap(stat -> Optional.ofNullable(stat instanceof J.Return ? (J.Return) stat : null))
                        .map(J.Return::getExpr)
                        .map(r -> isLiteralFalse(r) || isLiteralTrue(r))
                        .orElse(true),
                super::visitIf,
                i -> (Statement) i,
                (i, cursor) -> {
                    J.If iff2 = (J.If) i;
                    J.Return retrn = getReturnIfOnlyStatementInThen(iff).orElseThrow();
                    Expression ifCondition = ((J.If) i).getIfCondition().getTree();

                    if (isLiteralTrue(retrn.getExpr()) && getReturnExprIfOnlyStatementInElseThen(iff2)
                            .map(this::isLiteralFalse).orElse(true)) {
                        if(iff2.getElsePart() != null) {
                            deleteStatement(iff2.getElsePart().getStatement());
                        }

                        singleFollowingStatement(cursor).ifPresent(this::deleteStatement);

                        return retrn
                                .withExpr(ifCondition.withFormatting(format(" ")))
                                .withFormatting(i.getFormatting());
                    } else if (isLiteralFalse(retrn.getExpr()) && getReturnExprIfOnlyStatementInElseThen(iff2)
                            .map(this::isLiteralTrue).orElse(true)) {
                        if(iff2.getElsePart() != null) {
                            deleteStatement(iff2.getElsePart().getStatement());
                        }

                        singleFollowingStatement(cursor).ifPresent(this::deleteStatement);

                        //  we need to NOT the expression inside the if condition

                        var maybeParenthesizedCondition = ifCondition instanceof J.Binary ?
                                new J.Parentheses<>(randomId(), ifCondition, EMPTY) :
                                ifCondition;

                        return retrn
                                .withExpr(new J.Unary(randomId(), new J.Unary.Operator.Not(randomId(), EMPTY),
                                        maybeParenthesizedCondition, Type.Primitive.Boolean, format(" ")))
                                .withFormatting(i.getFormatting());
                    }
                    return i;
                }
        );
    }

    private boolean thenHasOnlyReturnStatement(J.If iff) {
        return getReturnIfOnlyStatementInThen(iff)
                .map(retrn -> isLiteralFalse(retrn.getExpr()) || isLiteralTrue(retrn.getExpr()))
                .orElse(false);
    }

    private Optional<Statement> singleFollowingStatement(Cursor cursor) {
        J.Block<Statement> block = cursor.getParentOrThrow().getTree();
        List<Statement> following = block.getStatements().stream().dropWhile(s -> s != cursor.getTree())
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
