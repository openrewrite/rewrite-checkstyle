package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class FinalLocalVariableTest: Parser by OpenJdkParser() {
    @Test
    fun localVariablesAreMadeFinal() {
        val a = parse("""
            public class A {
                {
                    int n = 1;
                    for(int i = 0; i < n; i++) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(FinalLocalVariable()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    final int n = 1;
                    for(int i = 0; i < n; i++) {
                    }
                }
            }
        """)
    }

    @Test
    fun multiVariables() {
        val a = parse("""
            public class A {
                {
                    int a, b = 1;
                    a = 0;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(FinalLocalVariable()).fix().fixed

        // the final only applies to any initialized variables (b in this case)
        assertRefactored(fixed, """
            public class A {
                {
                    final int a, b = 1;
                    a = 0;
                }
            }
        """)
    }
}
