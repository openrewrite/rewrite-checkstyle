package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openrewrite.java.tree.J

class SpansMultipleLinesTest : JavaParser() {
    @Test
    fun spansMultipleLines() {
        val a = parse("""
            public class A {
                int m, n;
                {
                    { int n = 1; }
                    { int n = 2;
                    }

                    if(n == 1) {
                    }

                    if(n == 1 &&
                        m == 2) {
                    }
                }
            }
        """.trimIndent())

        val init = a.classes[0].body.statements[1] as J.Block<*>

        assertFalse(SpansMultipleLines(init.statements[0], null).visit(init.statements[0]))
        assertTrue(SpansMultipleLines(init.statements[1],null).visit(init.statements[1]))

        val iff = init.statements[2] as J.If
        assertFalse(SpansMultipleLines(iff, iff.thenPart).visit(iff))

        val iff2 = init.statements[3] as J.If
        assertTrue(SpansMultipleLines(iff2, iff2.thenPart).visit(iff2))
    }

    @Test
    fun classDecl() {
        val a = parse("""
            public class A {
                {
                }
            }
        """.trimIndent())

        val aClass = a.classes[0]
        assertFalse(SpansMultipleLines(aClass, aClass.body).visit(aClass))
    }
}
