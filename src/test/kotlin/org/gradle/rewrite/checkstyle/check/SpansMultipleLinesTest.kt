package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import com.netflix.rewrite.tree.Tr
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpansMultipleLinesTest : Parser by OpenJdkParser() {
    @Test
    fun spansMultipleLines() {
        val a = parse("""
            public class A {
                {
                    { int n = 1; }
                    { int n = 2;
                    }
                }
            }
        """.trimIndent())

        val init = a.classes[0].body.statements[0] as Tr.Block<*>

        assertFalse(SpansMultipleLines().visit(init.statements[0]))
        assertTrue(SpansMultipleLines().visit(init.statements[1]))
    }
}