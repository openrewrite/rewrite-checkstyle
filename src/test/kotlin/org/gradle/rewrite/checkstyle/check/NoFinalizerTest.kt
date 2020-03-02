package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class NoFinalizerTest : JavaParser() {
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