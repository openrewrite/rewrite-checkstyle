package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.*;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.Optional;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Formatting.format;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

public class SimplifyBooleanReturn extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.SimplifyBooleanReturn";
    }

    @Override
    public List<AstTransform> visitIf(Tr.If iff) {
        return maybeTransform(iff,
                thenHasOnlyReturnStatement(iff) && singleFollowingStatement(getCursor())
                        .flatMap(stat -> Optional.ofNullable(stat instanceof Tr.Return ? (Tr.Return) stat : null))
                        .map(Tr.Return::getExpr)
                        .map(r -> isLiteralFalse(r) || isLiteralTrue(r))
                        .orElse(true),
                super::visitIf,
                i -> (Statement) i,
                (i, cursor) -> {
                    Tr.If iff2 = (Tr.If) i;
                    Tr.Return retrn = getReturnIfOnlyStatementInThen(iff).orElseThrow();
                    Expression ifCondition = ((Tr.If) i).getIfCondition().getTree();

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

                        var maybeParenthesizedCondition = ifCondition instanceof Tr.Binary ?
                                new Tr.Parentheses<>(randomId(), ifCondition, EMPTY) :
                                ifCondition;

                        return retrn
                                .withExpr(new Tr.Unary(randomId(), new Tr.Unary.Operator.Not(randomId(), EMPTY),
                                        maybeParenthesizedCondition, Type.Primitive.Boolean, format(" ")))
                                .withFormatting(i.getFormatting());
                    }
                    return i;
                }
        );
    }

    private boolean thenHasOnlyReturnStatement(Tr.If iff) {
        return getReturnIfOnlyStatementInThen(iff)
                .map(retrn -> isLiteralFalse(retrn.getExpr()) || isLiteralTrue(retrn.getExpr()))
                .orElse(false);
    }

    private Optional<Statement> singleFollowingStatement(Cursor cursor) {
        Tr.Block<Statement> block = cursor.getParentOrThrow().getTree();
        List<Statement> following = block.getStatements().stream().dropWhile(s -> s != cursor.getTree())
                .skip(1).collect(toList());
        return Optional.ofNullable(following.size() != 1 ? null : following.get(0));
    }

    private boolean isLiteralTrue(Expression expression) {
        return expression instanceof Tr.Literal && ((Tr.Literal) expression).getValue() == Boolean.valueOf(true);
    }

    private boolean isLiteralFalse(Expression expression) {
        return expression instanceof Tr.Literal && ((Tr.Literal) expression).getValue() == Boolean.valueOf(false);
    }

    private Optional<Tr.Return> getReturnIfOnlyStatementInThen(Tr.If iff) {
        if (iff.getThenPart() instanceof Tr.Return) {
            return Optional.of((Tr.Return) iff.getThenPart());
        }
        if (iff.getThenPart() instanceof Tr.Block) {
            Tr.Block<?> then = (Tr.Block<?>) iff.getThenPart();
            if (then.getStatements().size() == 1 && then.getStatements().get(0) instanceof Tr.Return) {
                return Optional.of((Tr.Return) then.getStatements().get(0));
            }
        }
        return Optional.empty();
    }

    private Optional<Expression> getReturnExprIfOnlyStatementInElseThen(Tr.If iff2) {
        return ofNullable(iff2.getElsePart())
                .flatMap(elze -> ofNullable(elze.getStatement() instanceof Tr.If ? (Tr.If) elze.getStatement() : null))
                .flatMap(this::getReturnIfOnlyStatementInThen)
                .map(Tr.Return::getExpr);
    }
}
