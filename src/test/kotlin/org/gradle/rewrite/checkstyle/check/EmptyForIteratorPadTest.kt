package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.PadPolicy
import org.junit.jupiter.api.Test

open class EmptyForIteratorPadTest: Parser by OpenJdkParser() {
    @Test
    fun noSpaceInitializerPadding() {
        val a = parse("""
            public class A {
                {
                    for (int i = 0; i < 2; i++ );
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyForIteratorPad()).fix()

        assertRefactored(fixed, """
            public class A {
                {
                    for (int i = 0; i < 2; i++);
                }
            }
        """)
    }

    @Test
    fun spaceInitializerPadding() {
        val a = parse("""
            public class A {
                {
                    for (int i = 0; i < 2; i++);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyForIteratorPad(PadPolicy.SPACE)).fix()

        assertRefactored(fixed, """
            public class A {
                {
                    for (int i = 0; i < 2; i++ );
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

        val fixed = a.refactor().run(EmptyForIteratorPad()).fix()

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
