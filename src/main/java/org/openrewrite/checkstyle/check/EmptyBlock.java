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
package org.openrewrite.checkstyle.check;

import org.eclipse.microprofile.config.Config;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.checkstyle.policy.BlockPolicy;
import org.openrewrite.checkstyle.policy.Token;
import org.openrewrite.java.refactor.DeleteStatement;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

/**
 * TODO offer option to log if a logger field is available instead of rethrowing as an unchecked exception.
 */
public class EmptyBlock extends CheckstyleRefactorVisitor {
    private static final Set<Token> DEFAULT_TOKENS = Set.of(
            Token.LITERAL_WHILE,
            Token.LITERAL_TRY,
            Token.LITERAL_FINALLY,
            Token.LITERAL_DO,
            Token.LITERAL_IF,
            Token.LITERAL_ELSE,
            Token.LITERAL_FOR,
            Token.INSTANCE_INIT,
            Token.STATIC_INIT,
            Token.LITERAL_SWITCH,
            Token.LITERAL_SYNCHRONIZED
    );

    private final BlockPolicy block;
    private final Set<Token> tokens;

    public EmptyBlock(BlockPolicy block, Set<Token> tokens) {
        super("checkstyle.EmptyBlock");
        this.block = block;
        this.tokens = tokens;
        setCursoringOn();
    }

    @AutoConfigure
    public static EmptyBlock configure(Config config) {
        return fromModule(
                config,
                "EmptyBlock",
                m -> new EmptyBlock(
                        m.propAsOptionValue(BlockPolicy::valueOf, BlockPolicy.Statement),
                        m.propAsTokens(Token.class, DEFAULT_TOKENS)
                )
        );
    }

    @SuppressWarnings("unchecked")
    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop) {
        J.WhileLoop w = refactor(whileLoop, super::visitWhileLoop);

        if (tokens.contains(Token.LITERAL_WHILE) && isEmptyBlock(w.getBody())) {
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

        if (tokens.contains(Token.LITERAL_WHILE) && isEmptyBlock(w.getBody())) {
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
                    if (!(s instanceof J.Block)) {
                        return s;
                    }

                    J.Block<?> nestedBlock = (J.Block<?>) s;
                    if (isEmptyBlock(nestedBlock) &&
                            ((tokens.contains(Token.STATIC_INIT) && nestedBlock.getStatic() != null) ||
                                    (tokens.contains(Token.INSTANCE_INIT) && nestedBlock.getStatic() == null))) {
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

        if (tokens.contains(Token.LITERAL_CATCH) && isEmptyBlock(c.getBody())) {
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

        if (tokens.contains(Token.LITERAL_TRY) && isEmptyBlock(t.getBody())) {
            andThen(new DeleteStatement(tryable));
        } else if (tokens.contains(Token.LITERAL_FINALLY) && t.getFinally() != null && isEmptyBlock(t.getFinally().getBody())) {
            t = t.withFinally(null);
        }

        return t;
    }

    @SuppressWarnings("unchecked")
    @Override
    public J visitIf(J.If iff) {
        J.If i = refactor(iff, super::visitIf);

        if (!tokens.contains(Token.LITERAL_IF) || !isEmptyBlock(i.getThenPart())) {
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
        if (tokens.contains(Token.LITERAL_SYNCHRONIZED) && isEmptyBlock(synch.getBody())) {
            andThen(new DeleteStatement(synch));
        }

        return super.visitSynchronized(synch);
    }

    @Override
    public J visitSwitch(J.Switch switzh) {
        if (tokens.contains(Token.LITERAL_SWITCH) && isEmptyBlock(switzh.getCases())) {
            andThen(new DeleteStatement(switzh));
        }

        return super.visitSwitch(switzh);
    }

    private boolean isEmptyBlock(Statement blockNode) {
        return block.equals(BlockPolicy.Statement) &&
                blockNode instanceof J.Block &&
                ((J.Block<?>) blockNode).getStatements().isEmpty();
    }

    private static class ExtractSideEffectsOfIfCondition extends JavaRefactorVisitor {
        private final J.Block<?> enclosingBlock;
        private final J.If toExtract;

        public ExtractSideEffectsOfIfCondition(J.Block<?> enclosingBlock, J.If toExtract) {
            super("checkstyle.ExtractSideEffectsOfIfCondition");
            this.enclosingBlock = enclosingBlock;
            this.toExtract = toExtract;
        }

        @Override
        public J visitBlock(J.Block<J> block) {
            J.Block<J> b = refactor(block, super::visitBlock);
            if (enclosingBlock.isScope(block)) {
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
