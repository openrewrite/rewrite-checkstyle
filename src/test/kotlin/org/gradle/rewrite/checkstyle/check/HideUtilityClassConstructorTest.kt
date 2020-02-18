package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class HideUtilityClassConstructorTest : Parser by OpenJdkParser() {
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
