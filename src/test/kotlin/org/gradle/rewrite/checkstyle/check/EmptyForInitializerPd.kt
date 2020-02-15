package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.PadPolicy
import org.junit.jupiter.api.Test

open class EmptyForInitializerPd: Parser by OpenJdkParser() {
    @Test
    fun noSpaceInitializerPadding() {
        val a = parse("""
            public class A {
                {
                    for (; i < j; i++, j--);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyForInitializerPad()).fix()

        assertRefactored(fixed, """
            public class A {
                {
                    for (; i < j; i++, j--);
                }
            }
        """)
    }

    @Test
    fun spaceInitializerPadding() {
        val a = parse("""
            public class A {
                {
                    for (; i < j; i++, j--);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyForInitializerPad(PadPolicy.SPACE)).fix()

        assertRefactored(fixed, """
            public class A {
                {
                    for ( ; i < j; i++, j--);
                }
            }
        """)
    }

    @Test
    fun noCheckIfInitializerStartsWithLineTerminator() {
        val a = parse("""
            public class A {
                {
                    for (
                          ; i < j; i++, j--);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyForInitializerPad()).fix()

        assertRefactored(fixed, """
            public class A {
                {
                    for (
                          ; i < j; i++, j--);
                }
            }
        """)
    }
}
