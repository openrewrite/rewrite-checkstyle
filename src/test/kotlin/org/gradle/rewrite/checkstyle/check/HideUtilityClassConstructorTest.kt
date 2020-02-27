package org.gradle.rewrite.checkstyle.check

import org.openrewrite.Parser
import org.junit.jupiter.api.Test

open class HideUtilityClassConstructorTest : Parser() {
    @Test
    fun hideUtilityConstructor() {
        val a = parse("""
            public class A {
                public A() {
                }
                
                public static void utility() {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(HideUtilityClassConstructor()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                private A() {
                }
                
                public static void utility() {
                }
            }
        """)
    }
}
