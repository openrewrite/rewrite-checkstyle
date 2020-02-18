package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class NoFinalizerTest : Parser by OpenJdkParser() {
    @Test
    fun noFinalizer() {
        val a = parse("""
            public class A {
                @Override
                protected void finalize() throws Throwable {
                    super.finalize();
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NoFinalizer()).fix().fixed

        assertRefactored(fixed, """
            public class A {
            }
        """)
    }
}