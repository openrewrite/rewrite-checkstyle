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
package org.openrewrite.checkstyle;

import org.openrewrite.Cursor;
import org.openrewrite.AutoConfigure;
import org.openrewrite.java.DeleteStatement;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class SimplifyBooleanReturn extends CheckstyleRefactorVisitor {
    public SimplifyBooleanReturn() {
        setCursoringOn();
    }

    @Override
    public J visitIf(J.If iff) {
        J.If i = refactor(iff, super::visitIf);

        Cursor parent = getCursor().getParentOrThrow();

        if (parent.getTree() instanceof J.Block &&
                parent.getParentOrThrow().getTree() instanceof J.MethodDecl &&
                thenHasOnlyReturnStatement(iff) &&
                elseWithOnlyReturn(i)) {
            List<Statement> followingStatements = followingStatements();
            Optional<Expression> singleFollowingStatement = ofNullable(followingStatements.isEmpty() ? null : followingStatements.get(0))
                    .flatMap(stat -> Optional.ofNullable(stat instanceof J.Return ? (J.Return) stat : null))
                    .map(J.Return::getExpr);

            if (followingStatements.isEmpty() || singleFollowingStatement.map(r -> isLiteralFalse(r) || isLiteralTrue(r)).orElse(false)) {
                J.Return retrn = getReturnIfOnlyStatementInThen(iff).orElse(null);
                if(retrn == null) {
                    throw new NoSuchElementException("No return statement");
                }

                Expression ifCondition = i.getIfCondition().getTree();

                if (isLiteralTrue(retrn.getExpr())) {
                    if (singleFollowingStatement.map(this::isLiteralFalse).orElse(false) && i.getElsePart() == null) {
                        andThen(new DeleteStatement(followingStatements.get(0)));
                        return retrn
                                .withExpr(ifCondition.withFormatting(format(" ")))
                                .withFormatting(i.getFormatting());
                    } else if (!singleFollowingStatement.isPresent() &&
                            getReturnExprIfOnlyStatementInElseThen(i).map(this::isLiteralFalse).orElse(false)) {
                        if (i.getElsePart() != null) {
                            andThen(new DeleteStatement(i.getElsePart().getStatement()));
                        }

                        return retrn
                                .withExpr(ifCondition.withFormatting(format(" ")))
                                .withFormatting(i.getFormatting());
                    }
                } else if (isLiteralFalse(retrn.getExpr())) {
                    boolean returnThenPart = false;

                    if (singleFollowingStatement.map(this::isLiteralTrue).orElse(false) && i.getElsePart() == null) {
                        andThen(new DeleteStatement(followingStatements.get(0)));
                        returnThenPart = true;
                    } else if (!singleFollowingStatement.isPresent() && getReturnExprIfOnlyStatementInElseThen(i)
                            .map(this::isLiteralTrue).orElse(false)) {
                        if (i.getElsePart() != null) {
                            andThen(new DeleteStatement(i.getElsePart().getStatement()));
                        }
                        returnThenPart = true;
                    }

                    if (returnThenPart) {
                        //  we need to NOT the expression inside the if condition
                        Expression maybeParenthesizedCondition = ifCondition instanceof J.Binary || ifCondition instanceof J.Ternary ?
                                new J.Parentheses<>(randomId(), ifCondition, EMPTY) :
                                ifCondition;

                        return retrn
                                .withExpr(new J.Unary(randomId(), new J.Unary.Operator.Not(randomId(), EMPTY),
                                        maybeParenthesizedCondition, JavaType.Primitive.Boolean, format(" ")))
                                .withFormatting(i.getFormatting());
                    }
                }
            }
        }

        return i;
    }

    private boolean elseWithOnlyReturn(J.If i) {
        return i.getElsePart() == null || !(i.getElsePart().getStatement() instanceof J.If);
    }

    private boolean thenHasOnlyReturnStatement(J.If iff) {
        return getReturnIfOnlyStatementInThen(iff)
                .map(retrn -> isLiteralFalse(retrn.getExpr()) || isLiteralTrue(retrn.getExpr()))
                .orElse(false);
    }

    private List<Statement> followingStatements() {
        J.Block<Statement> block = getCursor().getParentOrThrow().getTree();
        AtomicBoolean dropWhile = new AtomicBoolean(false);
        return block.getStatements().stream()
                .filter(s -> {
                    dropWhile.set(dropWhile.get() || s == getCursor().getTree());
                    return dropWhile.get();
                })
                .skip(1).collect(toList());
    }

    private boolean isLiteralTrue(J tree) {
        return tree instanceof J.Literal && ((J.Literal) tree).getValue() == Boolean.valueOf(true);
    }

    private boolean isLiteralFalse(J tree) {
        return tree instanceof J.Literal && ((J.Literal) tree).getValue() == Boolean.valueOf(false);
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
        if (iff2.getElsePart() == null) {
            return Optional.empty();
        }

        Statement elze = iff2.getElsePart().getStatement();
        if (elze instanceof J.Return) {
            return ofNullable(((J.Return) elze).getExpr());
        }

        if (elze instanceof J.Block) {
            List<? extends J> statements = ((J.Block<? extends J>) elze).getStatements();
            if (statements.size() == 1) {
                J statement = statements.get(0);
                if (statement instanceof J.Return) {
                    return ofNullable(((J.Return) statement).getExpr());
                }
            }
        }

        return Optional.empty();
    }
}
