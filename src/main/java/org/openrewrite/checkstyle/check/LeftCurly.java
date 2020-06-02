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
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.checkstyle.policy.LeftCurlyPolicy;
import org.openrewrite.checkstyle.policy.Token;
import org.openrewrite.java.tree.J;

import java.util.Set;

public class LeftCurly extends CheckstyleRefactorVisitor {
    private static final Set<Token> DEFAULT_TOKENS =Set.of(
            Token.ANNOTATION_DEF,
            Token.CLASS_DEF,
            Token.CTOR_DEF,
            Token.ENUM_CONSTANT_DEF,
            Token.ENUM_DEF,
            Token.INTERFACE_DEF,
            Token.LAMBDA,
            Token.LITERAL_CASE,
            Token.LITERAL_CATCH,
            Token.LITERAL_DEFAULT,
            Token.LITERAL_DO,
            Token.LITERAL_ELSE,
            Token.LITERAL_FINALLY,
            Token.LITERAL_FOR,
            Token.LITERAL_IF,
            Token.LITERAL_SWITCH,
            Token.LITERAL_SYNCHRONIZED,
            Token.LITERAL_TRY,
            Token.LITERAL_WHILE,
            Token.METHOD_DEF,
            Token.OBJBLOCK,
            Token.STATIC_INIT
    );

    private final LeftCurlyPolicy option;
    private final boolean ignoreEnums;
    private final Set<Token> tokens;

    public LeftCurly(LeftCurlyPolicy option, boolean ignoreEnums, Set<Token> tokens) {
        super("checkstyle.LeftCurly");
        this.option = option;
        this.ignoreEnums = ignoreEnums;
        this.tokens = tokens;
        setCursoringOn();
    }

    @AutoConfigure
    public static LeftCurly configure(Config config) {
        return fromModule(
                config,
                "LeftCurly",
                m -> new LeftCurly(
                        m.propAsOptionValue(LeftCurlyPolicy::valueOf, LeftCurlyPolicy.EOL),
                        m.prop("ignoreEnums", false),
                        m.propAsTokens(Token.class, DEFAULT_TOKENS)
                )
        );
    }

    @Override
    public J visitBlock(J.Block<J> block) {
        J.Block<J> b = refactor(block, super::visitBlock);

        Cursor containing = getCursor().getParentOrThrow();

        boolean spansMultipleLines = LeftCurlyPolicy.NLOW.equals(option) ?
                new SpansMultipleLines(containing.getTree(), block).visit((Tree) containing.getTree()) : false;

        if (!satisfiesPolicy(option, block, containing.getTree(), spansMultipleLines)) {
            b = formatCurly(option, b, spansMultipleLines, containing);
        }

        return b;
    }

    private boolean satisfiesPolicy(LeftCurlyPolicy option, J.Block<J> block, Tree containing, boolean spansMultipleLines) {
        switch (option) {
            case EOL:
                if (ignoreEnums && containing instanceof J.Case) {
                    return true;
                }

                if (block.getStatic() == null) {
                    return !block.getFormatting().getPrefix().contains("\n");
                } else {
                    return !block.getStatic().getFormatting().getSuffix().contains("\n");
                }
            case NL:
                if (block.getStatic() == null) {
                    return block.getFormatting().getPrefix().contains("\n");
                } else {
                    return block.getStatic().getFormatting().getSuffix().contains("\n");
                }
            case NLOW:
            default:
                return (spansMultipleLines && satisfiesPolicy(LeftCurlyPolicy.NL, block, containing, true)) ||
                        (!spansMultipleLines && satisfiesPolicy(LeftCurlyPolicy.EOL, block, containing, false));
        }
    }

    private static J.Block<J> formatCurly(LeftCurlyPolicy option, J.Block<J> block, boolean spansMultipleLines, Cursor containing) {
        switch (option) {
            case EOL:
                // a non-static class initializer can remain as it is
                Tree parent = containing.getParentOrThrow().getTree();
                if ((parent instanceof J.ClassDecl || parent instanceof J.NewClass) && block.getStatic() == null) {
                    return block;
                }

                return block.getStatic() == null ? block.withPrefix(" ") : block.withStatic(block.getStatic().withSuffix(" "));
            case NL:
                return block.getStatic() == null ? block.withPrefix(block.getEndOfBlockSuffix()) :
                        block.withStatic(block.getStatic().withSuffix(block.getEndOfBlockSuffix()));
            case NLOW:
            default:
                return formatCurly(spansMultipleLines ? LeftCurlyPolicy.NL : LeftCurlyPolicy.EOL, block, spansMultipleLines, containing);
        }
    }
}
