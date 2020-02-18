package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class FinalClassTest : Parser by OpenJdkParser() {
    @Test
    fun shouldBeFinalClass() {
        val a = parse("""
            public class A {
                private A(String s) {
                }
                
                private A() {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(FinalClass()).fix().fixed

        assertRefactored(fixed, """
            public final class A {
                private A(String s) {
                }
                
                private A() {
                }
            }
        """)
    }

    @Test
    fun shouldNotBeFinalClass() {
        val a = parse("""
            public class A {
                private A(String s) {
                }
                
                public A() {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(FinalClass()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                private A(String s) {
                }
                
                public A() {
                }
            }
        """)
    }
}
