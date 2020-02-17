package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.*;
import com.netflix.rewrite.tree.visitor.RetrieveTreeVisitor;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.BlockPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Formatting.format;
import static com.netflix.rewrite.tree.Tr.randomId;
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
        return "EmptyBlock";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitWhileLoop(Tr.WhileLoop whileLoop) {
        return maybeTransform(tokens.contains(LITERAL_WHILE) && isEmptyBlock(whileLoop.getBody()),
                super.visitWhileLoop(whileLoop),
                transform(whileLoop.getBody(), b -> {
                    Tr.Block<Tree> block = (Tr.Block<Tree>) b;
                    return block.withStatements(
                            singletonList(new Tr.Continue(randomId(), null, formatter().format(block))));
                })
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AstTransform> visitDoWhileLoop(Tr.DoWhileLoop doWhileLoop) {
        return maybeTransform(tokens.contains(LITERAL_DO) && isEmptyBlock(doWhileLoop.getBody()),
                super.visitDoWhileLoop(doWhileLoop),
                transform(doWhileLoop.getBody(), b -> {
                    Tr.Block<Tree> block = (Tr.Block<Tree>) b;
                    return block.withStatements(
                            singletonList(new Tr.Continue(randomId(), null, formatter().format(block))));
                })
        );
    }

    @Override
    public List<AstTransform> visitBlock(Tr.Block<Tree> block) {
        return Optional.ofNullable(getCursor().getParentOrThrow().getParent())
                .map(Cursor::getTree)
                .filter(t -> t instanceof Tr.ClassDecl && isEmptyBlock(block))
                .filter(t -> (tokens.contains(STATIC_INIT) && block.getStatic() != null) ||
                        (tokens.contains(INSTANCE_INIT) && block.getStatic() == null))
                .map(t -> maybeTransform(true, super.visitBlock(block),
                        transform(((Tr.ClassDecl) t).getBody(), body -> body
                                .withStatements(body.getStatements().stream()
                                        .filter(s -> s != block)
                                        .collect(toList())))))
                .orElse(super.visitBlock(block));
    }

    @Override
    public List<AstTransform> visitCatch(Tr.Try.Catch catzh) {
        return maybeTransform(tokens.contains(LITERAL_CATCH) && isEmptyBlock(catzh.getBody()),
                super.visitCatch(catzh),
                transform(catzh, c -> {
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
                                    singletonList(new Tr.Throw(randomId(),
                                            new Tr.NewClass(randomId(),
                                                    Tr.Ident.build(randomId(), throwName, throwClass, format(" ")),
                                                    new Tr.NewClass.Arguments(randomId(),
                                                            singletonList(Tr.Ident.build(randomId(), c.getParam().getTree().getVars().iterator().next().getSimpleName(),
                                                                    exceptionType == null ? null : exceptionType.getType(), EMPTY)),
                                                            EMPTY),
                                                    null,
                                                    throwClass,
                                                    format(" ")
                                            ),
                                            formatter().format(c.getBody())))
                            )
                    );
                }));
    }

    @Override
    public List<AstTransform> visitTry(Tr.Try tryable) {
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
    public List<AstTransform> visitIf(Tr.If iff) {
        List<AstTransform> changes = super.visitIf(iff);

        if (!tokens.contains(LITERAL_IF) || !isEmptyBlock(iff.getThenPart())) {
            return changes;
        }

        Tree containing = getCursor().getParentOrThrow().getTree();

        if (iff.getElsePart() == null) {
            // extract side effects from condition (if there are any).
            changes.addAll(transform(containing, enclosing -> {
                        if (enclosing instanceof Tr.Block) {
                            Tr.Block<Tree> enclosingBlock = (Tr.Block<Tree>) enclosing;
                            Tr.If iff2 = (Tr.If) new RetrieveTreeVisitor(iff.getId()).visit(enclosing);

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
            if (cond.getTree() instanceof Tr.Binary) {
                Tr.Binary binary = (Tr.Binary) cond.getTree();

                // only boolean operators are valid for if conditions
                Tr.Binary.Operator op = binary.getOperator();
                if (op instanceof Tr.Binary.Operator.Equal) {
                    return cond.withTree(binary.withOperator(new Tr.Binary.Operator.NotEqual(op.getId(), op.getFormatting())));
                } else if (op instanceof Tr.Binary.Operator.NotEqual) {
                    return cond.withTree(binary.withOperator(new Tr.Binary.Operator.Equal(op.getId(), op.getFormatting())));
                } else if (op instanceof Tr.Binary.Operator.LessThan) {
                    return cond.withTree(binary.withOperator(new Tr.Binary.Operator.GreaterThanOrEqual(op.getId(), op.getFormatting())));
                } else if (op instanceof Tr.Binary.Operator.LessThanOrEqual) {
                    return cond.withTree(binary.withOperator(new Tr.Binary.Operator.GreaterThan(op.getId(), op.getFormatting())));
                } else if (op instanceof Tr.Binary.Operator.GreaterThan) {
                    return cond.withTree(binary.withOperator(new Tr.Binary.Operator.LessThanOrEqual(op.getId(), op.getFormatting())));
                } else if (op instanceof Tr.Binary.Operator.GreaterThanOrEqual) {
                    return cond.withTree(binary.withOperator(new Tr.Binary.Operator.LessThan(op.getId(), op.getFormatting())));
                }
            }
            return cond;
        }));

        changes.addAll(transform(iff, i -> {
            if (i.getElsePart() == null) {
                return i.withThenPart(new Tr.Empty(randomId(), EMPTY)).withElsePart(null);
            }

            Tr.Block<Tree> thenPart = (Tr.Block<Tree>) i.getThenPart();

            Statement elseStatement = i.getElsePart().getStatement();
            List<Tree> elseStatementBody;
            if(elseStatement instanceof Tr.Block) {
                // any else statements should already be at the correct indentation level
                elseStatementBody = ((Tr.Block<Tree>) elseStatement).getStatements();
            }
            else if(elseStatement instanceof Tr.If) {
                // Tr.If will typically just have a format of one space (the space between "else" and "if" in "else if")
                // we want this to be on its own line now inside its containing if block
                String prefix = "\n" + range(0, thenPart.getIndent()).mapToObj(n -> " ").collect(joining(""));
                elseStatementBody = singletonList(elseStatement.withPrefix(prefix));
                andThen(formatter().shiftRight(elseStatement, i.getThenPart(), containing));
            }
            else {
                elseStatementBody = singletonList(elseStatement);
                andThen(formatter().shiftRight(elseStatement, i.getThenPart(), containing));
            }

            return i
                    .withThenPart(
                            // NOTE: then part MUST be a Tr.Block, because otherwise impossible to have an empty if condition followed by else-if/else chain
                            thenPart.withStatements(elseStatementBody))
                    .withElsePart(null);
        }));

        return changes;
    }

    @Override
    public List<AstTransform> visitSynchronized(Tr.Synchronized synch) {
        if (tokens.contains(LITERAL_SYNCHRONIZED) && isEmptyBlock(synch.getBody())) {
            deleteStatement(synch);
        }

        return super.visitSynchronized(synch);
    }

    @Override
    public List<AstTransform> visitSwitch(Tr.Switch switzh) {
        if(tokens.contains(LITERAL_SWITCH) && isEmptyBlock(switzh.getCases())) {
            deleteStatement(switzh);
        }

        return super.visitSwitch(switzh);
    }

    private boolean isEmptyBlock(Statement blockNode) {
        return block.equals(BlockPolicy.Statement) &&
                blockNode instanceof Tr.Block &&
                ((Tr.Block<?>) blockNode).getStatements().isEmpty();
    }
}
