package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.BlockPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.Cursor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.visitor.refactor.DeleteStatement;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;
import org.openrewrite.java.visitor.refactor.ScopedJavaRefactorVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static org.gradle.rewrite.checkstyle.policy.Token.*;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

/**
 * TODO offer option to log if a logger field is available instead of rethrowing as an unchecked exception.
 */
@Builder
public class EmptyBlock extends JavaRefactorVisitor {
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
    public String getName() {
        return "checkstyle.EmptyBlock";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop) {
        J.WhileLoop w = refactor(whileLoop, super::visitWhileLoop);

        if (tokens.contains(LITERAL_WHILE) && isEmptyBlock(w.getBody())) {
            J.Block<J> body = (J.Block<J>) w.getBody();
            w = w.withBody(body.withStatements(singletonList(new J.Continue(randomId(), null,
                    formatter.format(body)))));
        }

        return w;
    }

    @SuppressWarnings("unchecked")
    @Override
    public J visitDoWhileLoop(J.DoWhileLoop doWhileLoop) {
        J.DoWhileLoop w = refactor(doWhileLoop, super::visitDoWhileLoop);

        if (tokens.contains(LITERAL_WHILE) && isEmptyBlock(w.getBody())) {
            J.Block<J> body = (J.Block<J>) w.getBody();
            w = w.withBody(body.withStatements(singletonList(new J.Continue(randomId(), null,
                    formatter.format(body)))));
        }

        return w;
    }

    @Override
    public J visitBlock(J.Block<J> block) {
        J.Block<J> b = refactor(block, super::visitBlock);

        AtomicBoolean filtered = new AtomicBoolean(false);
        List<J> statements = b.getStatements().stream()
                .map(s -> {
                    if(!(s instanceof J.Block)) {
                        return s;
                    }

                    J.Block<?> nestedBlock = (J.Block<?>) s;
                    if (isEmptyBlock(nestedBlock) &&
                            ((tokens.contains(STATIC_INIT) && nestedBlock.getStatic() != null) ||
                                    (tokens.contains(INSTANCE_INIT) && nestedBlock.getStatic() == null))) {
                        filtered.set(true);
                        return null;
                    }

                    return nestedBlock;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return filtered.get() ? b.withStatements(statements) : b;
    }

    @Override
    public J visitCatch(J.Try.Catch catzh) {
        J.Try.Catch c = refactor(catzh, super::visitCatch);

        if (tokens.contains(LITERAL_CATCH) && isEmptyBlock(c.getBody())) {
            var exceptionType = c.getParam().getTree().getTypeExpr();
            var exceptionClassType = exceptionType == null ? null : TypeUtils.asClass(exceptionType.getType());

            final String throwName;
            final JavaType.Class throwClass;

            if (exceptionClassType != null && exceptionClassType.getFullyQualifiedName().equals("java.io.IOException")) {
                throwName = "UncheckedIOException";
                throwClass = JavaType.Class.build("java.io.UncheckedIOException");
                maybeAddImport(throwClass);
            } else {
                throwName = "RuntimeException";
                throwClass = JavaType.Class.build("java.lang.RuntimeException");
            }

            c = c.withBody(
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
                                    formatter.format(c.getBody())))
                    )
            );
        }

        return c;
    }

    @Override
    public J visitTry(J.Try tryable) {
        J.Try t = refactor(tryable, super::visitTry);

        if (tokens.contains(LITERAL_TRY) && isEmptyBlock(t.getBody())) {
            andThen(new DeleteStatement(tryable));
        } else if (tokens.contains(LITERAL_FINALLY) && t.getFinally() != null && isEmptyBlock(t.getFinally().getBody())) {
            t = t.withFinally(null);
        }

        return t;
    }

    @SuppressWarnings("unchecked")
    @Override
    public J visitIf(J.If iff) {
        J.If i = refactor(iff, super::visitIf);

        if (!tokens.contains(LITERAL_IF) || !isEmptyBlock(i.getThenPart())) {
            return i;
        }

        if (i.getElsePart() == null) {
            // extract side effects from condition (if there are any).
            andThen(new ExtractSideEffectsOfIfCondition(enclosingBlock(), i));
            return i;
        }

        // invert top-level if
        J.Parentheses<Expression> cond = i.getIfCondition();
        if (cond.getTree() instanceof J.Binary) {
            J.Binary binary = (J.Binary) cond.getTree();

            // only boolean operators are valid for if conditions
            J.Binary.Operator op = binary.getOperator();
            if (op instanceof J.Binary.Operator.Equal) {
                cond = cond.withTree(binary.withOperator(new J.Binary.Operator.NotEqual(op.getId(), op.getFormatting())));
            } else if (op instanceof J.Binary.Operator.NotEqual) {
                cond = cond.withTree(binary.withOperator(new J.Binary.Operator.Equal(op.getId(), op.getFormatting())));
            } else if (op instanceof J.Binary.Operator.LessThan) {
                cond = cond.withTree(binary.withOperator(new J.Binary.Operator.GreaterThanOrEqual(op.getId(), op.getFormatting())));
            } else if (op instanceof J.Binary.Operator.LessThanOrEqual) {
                cond = cond.withTree(binary.withOperator(new J.Binary.Operator.GreaterThan(op.getId(), op.getFormatting())));
            } else if (op instanceof J.Binary.Operator.GreaterThan) {
                cond = cond.withTree(binary.withOperator(new J.Binary.Operator.LessThanOrEqual(op.getId(), op.getFormatting())));
            } else if (op instanceof J.Binary.Operator.GreaterThanOrEqual) {
                cond = cond.withTree(binary.withOperator(new J.Binary.Operator.LessThan(op.getId(), op.getFormatting())));
            }

            i = i.withIfCondition(cond);
        }

        if (i.getElsePart() == null) {
            return i.withThenPart(new J.Empty(randomId(), EMPTY)).withElsePart(null);
        }

        // NOTE: then part MUST be a J.Block, because otherwise impossible to have an empty if condition followed by else-if/else chain
        J.Block<J> thenPart = (J.Block<J>) i.getThenPart();

        var containing = getCursor().getParentOrThrow().getTree();
        Statement elseStatement = i.getElsePart().getStatement();
        List<J> elseStatementBody;
        if (elseStatement instanceof J.Block) {
            // any else statements should already be at the correct indentation level
            elseStatementBody = ((J.Block<J>) elseStatement).getStatements();
        } else if (elseStatement instanceof J.If) {
            // J.If will typically just have a format of one space (the space between "else" and "if" in "else if")
            // we want this to be on its own line now inside its containing if block
            String prefix = "\n" + range(0, thenPart.getIndent()).mapToObj(n -> " ").collect(joining(""));
            elseStatementBody = singletonList(elseStatement.withPrefix(prefix));
            andThen(formatter.shiftRight(elseStatement, i.getThenPart(), containing));
        } else {
            elseStatementBody = singletonList(elseStatement);
            andThen(formatter.shiftRight(elseStatement, i.getThenPart(), containing));
        }

        return i.withThenPart(thenPart.withStatements(elseStatementBody))
                .withElsePart(null);
    }

    @Override
    public J visitSynchronized(J.Synchronized synch) {
        if (tokens.contains(LITERAL_SYNCHRONIZED) && isEmptyBlock(synch.getBody())) {
            andThen(new DeleteStatement(synch));
        }

        return super.visitSynchronized(synch);
    }

    @Override
    public J visitSwitch(J.Switch switzh) {
        if (tokens.contains(LITERAL_SWITCH) && isEmptyBlock(switzh.getCases())) {
            andThen(new DeleteStatement(switzh));
        }

        return super.visitSwitch(switzh);
    }

    private boolean isEmptyBlock(Statement blockNode) {
        return block.equals(BlockPolicy.Statement) &&
                blockNode instanceof J.Block &&
                ((J.Block<?>) blockNode).getStatements().isEmpty();
    }

    private static class ExtractSideEffectsOfIfCondition extends ScopedJavaRefactorVisitor {
        private final J.If toExtract;

        public ExtractSideEffectsOfIfCondition(J.Block<?> enclosingBlock, J.If toExtract) {
            super(enclosingBlock.getId());
            this.toExtract = toExtract;
        }

        @Override
        public J visitBlock(J.Block<J> block) {
            J.Block<J> b = refactor(block, super::visitBlock);
            if (isScope()) {
                List<J> statements = new ArrayList<>(b.getStatements().size());
                for (J statement : b.getStatements()) {
                    if (statement == toExtract) {
                        toExtract.getIfCondition().getTree().getSideEffects()
                                .stream()
                                .map(s -> (J) s.withFormatting(formatter.format(block)))
                                .forEach(statements::add);
                    } else {
                        statements.add(statement);
                    }
                }
                b = b.withStatements(statements);
            }
            return b;
        }
    }
}
