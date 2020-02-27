package org.gradle.rewrite.checkstyle.check

import org.openrewrite.Parser
import org.gradle.rewrite.checkstyle.policy.PadPolicy
import org.junit.jupiter.api.Test

open class EmptyForIteratorPadTest: Parser() {
    @Test
    fun doesntChangeIfIteratorIsPresent() {
        val a = parse("""
            public class A {
                {
                    for (int i = 0; i < 2; i++ );
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyForIteratorPad()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    for (int i = 0; i < 2; i++ );
                }
            }
        """)
    }

    @Test
    fun noSpaceInitializerPadding() {
        val a = parse("""
            public class A {
                {
                    for (int i = 0; i < 2; );
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyForIteratorPad()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    for (int i = 0; i < 2;);
                }
            }
        """)
    }

    @Test
    fun spaceInitializerPadding() {
        val a = parse("""
            public class A {
                {
                    for (int i = 0; i < 2;);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyForIteratorPad(PadPolicy.SPACE)).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    for (int i = 0; i < 2; );
                }
            }
        """)
    }

    @Test
    fun noCheckIfIteratorEndsWithLineTerminator() {
        val a = parse("""
            public class A {
                {
                    for (int i = 0; i < 2;
                        );
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyForIteratorPad()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    for (int i = 0; i < 2;
                        );
                }
            }
        """)
    }
}
