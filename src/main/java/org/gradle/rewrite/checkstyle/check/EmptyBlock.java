package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.*;
import org.openrewrite.visitor.RetrieveTreeVisitor;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.BlockPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.openrewrite.tree.Formatting.EMPTY;
import static org.openrewrite.tree.Formatting.format;
import static org.openrewrite.tree.J.randomId;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

/**
 * TODO offer option to log if a logger field is available instead of rethrowing as an unchecked exception.
 */
@Builder
public class EmptyBlock extends RefactorVisitor {
    @Builder.Default
    BlockPolicy block = BlockPolicy.Statement;

    @Builder.Default
    Set<Token> tokens = Set.of(
            LITERAL_WHILE,
            LITERAL_TRY,
            LITERAL_FINALLY,
            LITERAL_DO,
            LITERAL_IF,
            LITERAL_ELSE,
            LITERAL_FOR,
            INSTANCE_INIT,
            STATIC_INIT,
            LITERAL_SWITCH,
            LITERAL_SYNCHRONIZED
    );

    @Override
    public String getRuleName() {
        return "checkstyle.EmptyBlock";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitWhileLoop(J.WhileLoop whileLoop) {
        return maybeTransform(whileLoop,
                tokens.contains(LITERAL_WHILE) && isEmptyBlock(whileLoop.getBody()),
                super::visitWhileLoop,
                J.WhileLoop::getBody,
                b -> {
                    J.Block<Tree> block = (J.Block<Tree>) b;
                    return block.withStatements(
                            singletonList(new J.Continue(randomId(), null, formatter().format(block))));
                }
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitDoWhileLoop(J.DoWhileLoop doWhileLoop) {
        return maybeTransform(doWhileLoop,
                tokens.contains(LITERAL_DO) && isEmptyBlock(doWhileLoop.getBody()),
                super::visitDoWhileLoop,
                J.DoWhileLoop::getBody,
                b -> {
                    J.Block<Tree> block = (J.Block<Tree>) b;
                    return block.withStatements(
                            singletonList(new J.Continue(randomId(), null, formatter().format(block))));
                }
        );
    }

    @Override
    public List<AstTransform> visitBlock(J.Block<Tree> block) {
        Cursor containing = getCursor().getParentOrThrow();
        return maybeTransform(block,
                containing.getParent() != null &&
                        containing.getParent().getTree() instanceof J.ClassDecl &&
                        isEmptyBlock(block) &&
                        ((tokens.contains(STATIC_INIT) && block.getStatic() != null) ||
                                (tokens.contains(INSTANCE_INIT) && block.getStatic() == null)),
                super::visitBlock,
                b -> (J.Block<?>) containing.getTree(),
                body -> body.withStatements(body.getStatements().stream()
                        .filter(s -> s != block)
                        .collect(toList()))
        );
    }

    @Override
    public List<AstTransform> visitCatch(J.Try.Catch catzh) {
        return maybeTransform(catzh,
                tokens.contains(LITERAL_CATCH) && isEmptyBlock(catzh.getBody()),
                super::visitCatch,
                c -> {
                    var exceptionType = c.getParam().getTree().getTypeExpr();
                    var exceptionClassType = exceptionType == null ? null : TypeUtils.asClass(exceptionType.getType());

                    final String throwName;
                    final Type.Class throwClass;

                    if (exceptionClassType != null && exceptionClassType.getFullyQualifiedName().equals("java.io.IOException")) {
                        throwName = "UncheckedIOException";
                        throwClass = Type.Class.build("java.io.UncheckedIOException");
                        maybeAddImport(throwClass);
                    } else {
                        throwName = "RuntimeException";
                        throwClass = Type.Class.build("java.lang.RuntimeException");
                    }

                    return c.withBody(
                            c.getBody().withStatements(
                                    singletonList(new J.Throw(randomId(),
                                            new J.NewClass(randomId(),
                                                    J.Ident.build(randomId(), throwName, throwClass, format(" ")),
                                                    new J.NewClass.Arguments(randomId(),
                                                            singletonList(J.Ident.build(randomId(), c.getParam().getTree().getVars().iterator().next().getSimpleName(),
                                                                    exceptionType == null ? null : exceptionType.getType(), EMPTY)),
                                                            EMPTY),
                                                    null,
                                                    throwClass,
                                                    format(" ")
                                            ),
                                            formatter().format(c.getBody())))
                            )
                    );
                }
        );
    }

    @Override
    public List<AstTransform> visitTry(J.Try tryable) {
        List<AstTransform> changes = super.visitTry(tryable);

        if (tokens.contains(LITERAL_TRY) && isEmptyBlock(tryable.getBody())) {
            deleteStatement(tryable);
        } else if (tokens.contains(LITERAL_FINALLY) && tryable.getFinally() != null && isEmptyBlock(tryable.getFinally().getBody())) {
            changes.addAll(transform(tryable, t -> t.withFinallie(null)));
        }

        return changes;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitIf(J.If iff) {
        List<AstTransform> changes = super.visitIf(iff);

        if (!tokens.contains(LITERAL_IF) || !isEmptyBlock(iff.getThenPart())) {
            return changes;
        }

        if (iff.getElsePart() == null) {
            // extract side effects from condition (if there are any).
            changes.addAll(transform(getCursor().getParentOrThrow().getTree(), enclosing -> {
                        if (enclosing instanceof J.Block) {
                            J.Block<Tree> enclosingBlock = (J.Block<Tree>) enclosing;
                            J.If iff2 = (J.If) new RetrieveTreeVisitor(iff.getId()).visit(enclosing);

                            List<Tree> statements = new ArrayList<>(enclosingBlock.getStatements().size());
                            for (Tree statement : enclosingBlock.getStatements()) {
                                if (statement == iff2) {
                                    iff2.getIfCondition().getTree().getSideEffects()
                                            .stream()
                                            .map(s -> (Tree) s.withFormatting(formatter().format(enclosingBlock)))
                                            .forEach(statements::add);
                                } else {
                                    statements.add(statement);
                                }
                            }
                            return enclosingBlock.withStatements(statements);
                        } else {
                            return enclosing;
                        }
                    })
            );

            return changes;
        }

        // invert top-level if
        changes.addAll(transform(iff.getIfCondition(), cond -> {
            if (cond.getTree() instanceof J.Binary) {
                J.Binary binary = (J.Binary) cond.getTree();

                // only boolean operators are valid for if conditions
                J.Binary.Operator op = binary.getOperator();
                if (op instanceof J.Binary.Operator.Equal) {
                    return cond.withTree(binary.withOperator(new J.Binary.Operator.NotEqual(op.getId(), op.getFormatting())));
                } else if (op instanceof J.Binary.Operator.NotEqual) {
                    return cond.withTree(binary.withOperator(new J.Binary.Operator.Equal(op.getId(), op.getFormatting())));
                } else if (op instanceof J.Binary.Operator.LessThan) {
                    return cond.withTree(binary.withOperator(new J.Binary.Operator.GreaterThanOrEqual(op.getId(), op.getFormatting())));
                } else if (op instanceof J.Binary.Operator.LessThanOrEqual) {
                    return cond.withTree(binary.withOperator(new J.Binary.Operator.GreaterThan(op.getId(), op.getFormatting())));
                } else if (op instanceof J.Binary.Operator.GreaterThan) {
                    return cond.withTree(binary.withOperator(new J.Binary.Operator.LessThanOrEqual(op.getId(), op.getFormatting())));
                } else if (op instanceof J.Binary.Operator.GreaterThanOrEqual) {
                    return cond.withTree(binary.withOperator(new J.Binary.Operator.LessThan(op.getId(), op.getFormatting())));
                }
            }
            return cond;
        }));

        changes.addAll(transform(iff, (i, cursor) -> {
            if (i.getElsePart() == null) {
                return i.withThenPart(new J.Empty(randomId(), EMPTY)).withElsePart(null);
            }

            J.Block<Tree> thenPart = (J.Block<Tree>) i.getThenPart();

            var containing = cursor.getParentOrThrow().getTree();
            Statement elseStatement = i.getElsePart().getStatement();
            List<Tree> elseStatementBody;
            if (elseStatement instanceof J.Block) {
                // any else statements should already be at the correct indentation level
                elseStatementBody = ((J.Block<Tree>) elseStatement).getStatements();
            } else if (elseStatement instanceof J.If) {
                // J.If will typically just have a format of one space (the space between "else" and "if" in "else if")
                // we want this to be on its own line now inside its containing if block
                String prefix = "\n" + range(0, thenPart.getIndent()).mapToObj(n -> " ").collect(joining(""));
                elseStatementBody = singletonList(elseStatement.withPrefix(prefix));
                andThen(formatter().shiftRight(elseStatement, i.getThenPart(), containing));
            } else {
                elseStatementBody = singletonList(elseStatement);
                andThen(formatter().shiftRight(elseStatement, i.getThenPart(), containing));
            }

            return i
                    .withThenPart(
                            // NOTE: then part MUST be a J.Block, because otherwise impossible to have an empty if condition followed by else-if/else chain
                            thenPart.withStatements(elseStatementBody))
                    .withElsePart(null);
        }));

        return changes;
    }

    @Override
    public List<AstTransform> visitSynchronized(J.Synchronized synch) {
        if (tokens.contains(LITERAL_SYNCHRONIZED) && isEmptyBlock(synch.getBody())) {
            deleteStatement(synch);
        }

        return super.visitSynchronized(synch);
    }

    @Override
    public List<AstTransform> visitSwitch(J.Switch switzh) {
        if (tokens.contains(LITERAL_SWITCH) && isEmptyBlock(switzh.getCases())) {
            deleteStatement(switzh);
        }

        return super.visitSwitch(switzh);
    }

    private boolean isEmptyBlock(Statement blockNode) {
        return block.equals(BlockPolicy.Statement) &&
                blockNode instanceof J.Block &&
                ((J.Block<?>) blockNode).getStatements().isEmpty();
    }
}
