package org.gradle.rewrite.checkstyle.check

import org.openrewrite.Parser
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openrewrite.tree.J

class SpansMultipleLinesTest : Parser() {
    @Test
    fun spansMultipleLines() {
        val a = parse("""
            public class A {
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

        val init = a.classes[0].body.statements[0] as J.Block<*>

        assertFalse(SpansMultipleLines(null).visit(init.statements[0]))
        assertTrue(SpansMultipleLines(null).visit(init.statements[1]))

        val iff = init.statements[2] as J.If
        assertFalse(SpansMultipleLines(iff.thenPart).visit(iff))

        val iff2 = init.statements[3] as J.If
        assertTrue(SpansMultipleLines(iff2.thenPart).visit(iff2))
    }
}