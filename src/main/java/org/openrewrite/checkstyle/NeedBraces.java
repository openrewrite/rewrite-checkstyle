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

import org.openrewrite.AutoConfigure;
import org.openrewrite.Formatting;
import org.openrewrite.Tree;
import org.openrewrite.checkstyle.policy.Token;
import org.openrewrite.java.JavaFormatter;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toSet;
import static org.openrewrite.Formatting.format;
import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class NeedBraces extends CheckstyleRefactorVisitor {
    private static final Set<Token> DEFAULT_TOKENS = Stream.of(
            Token.LITERAL_DO, Token.LITERAL_ELSE, Token.LITERAL_FOR, Token.LITERAL_IF, Token.LITERAL_WHILE
    ).collect(toSet());

    private boolean allowSingleLineStatement;
    private boolean allowEmptyLoopBody;
    private Set<Token> tokens;

    public NeedBraces() {
        setCursoringOn();
    }

    @Override
    protected void configure(Module m) {
        this.allowSingleLineStatement = m.prop("allowSingleLineStatement", false);
        this.allowEmptyLoopBody = m.prop("allowEmptyLoopBody", false);
        this.tokens = m.propAsTokens(Token.class, DEFAULT_TOKENS);
    }

    @Override
    public J visitIf(J.If iff) {
        J.If i = refactor(iff, super::visitIf);

        if (tokens.contains(Token.LITERAL_IF) &&
                !(iff.getThenPart() instanceof J.Block) &&
                isNotAllowableSingleLine()) {
            i = i.withThenPart(addBraces(i.getThenPart()));
        }

        return i;
    }

    @Override
    public J visitElse(J.If.Else elze) {
        J.If.Else e = refactor(elze, super::visitElse);

        if (tokens.contains(Token.LITERAL_ELSE) &&
                !(elze.getStatement() instanceof J.If) &&
                !(elze.getStatement() instanceof J.Block) &&
                isNotAllowableSingleLine()) {
            e = e.withStatement(addBraces(e.getStatement()));
        }

        return e;
    }

    @Override
    public J visitWhileLoop(J.WhileLoop whileLoop) {
        J.WhileLoop w = refactor(whileLoop, super::visitWhileLoop);

        Statement body = w.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof J.Empty || body instanceof J.Block :
                body instanceof J.Block;

        if (tokens.contains(Token.LITERAL_WHILE) &&
                !hasAllowableBodyType &&
                isNotAllowableSingleLine()) {
            w = w.withBody(addBraces(w.getBody()));
        }

        return w;
    }

    @Override
    public J visitDoWhileLoop(J.DoWhileLoop doWhileLoop) {
        J.DoWhileLoop w = refactor(doWhileLoop, super::visitDoWhileLoop);

        if (tokens.contains(Token.LITERAL_DO) &&
                !(w.getBody() instanceof J.Block) &&
                isNotAllowableSingleLine()) {
            w = w.withBody(addBraces(w.getBody()));
        }

        return w;
    }

    @Override
    public J visitForLoop(J.ForLoop forLoop) {
        J.ForLoop f = refactor(forLoop, super::visitForLoop);

        Statement body = f.getBody();
        boolean hasAllowableBodyType = allowEmptyLoopBody ?
                body instanceof J.Empty || body instanceof J.Block :
                body instanceof J.Block;

        if (tokens.contains(Token.LITERAL_FOR) &&
                !hasAllowableBodyType &&
                isNotAllowableSingleLine()) {
            f = f.withBody(addBraces(f.getBody()));
        }

        return f;
    }

    private boolean isNotAllowableSingleLine() {
        return !allowSingleLineStatement || new SpansMultipleLines(getCursor().getTree(), null)
                .visit((Tree) getCursor().getTree());
    }

    @SuppressWarnings("ConstantConditions")
    private Statement addBraces(Statement body) {
        if (body instanceof J.Block) {
            return body;
        }

        int enclosingIndent = getCursor().getParentOrThrow().firstEnclosing(J.Block.class).getIndent();
        JavaFormatter.Result format = formatter.findIndent(enclosingIndent, getCursor().getParentOrThrow().getTree());

        String originalBodySuffix = body.getFormatting().getSuffix();

        return new J.Block<>(
                randomId(),
                null,
                body instanceof J.Empty ?
                        emptyList() :
                        singletonList(body.withFormatting(format(format.getPrefix(1)))),
                format(" ", originalBodySuffix),
                new J.Block.End(randomId(), Formatting.format(format.getPrefix()))
        );
    }
}
