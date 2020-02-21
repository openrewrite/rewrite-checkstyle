package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.Parser
import org.junit.jupiter.api.Test

open class MultipleVariableDeclarationsTest : Parser() {
    @Test
    fun replaceWithNonCovariantEquals() {
        val a = parse("""
            class Test {
                int n = 0, m = 0;
                int o = 0, p;
                
                {
                    Integer[] q = { 0 }, r[] = { { 0 } };
                    for(int i = 0, j = 0;;);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(MultipleVariableDeclarations()).fix().fixed

        assertRefactored(fixed, """
            class Test {
                int n = 0;
                int m = 0;
                int o = 0;
                int p;
                
                {
                    Integer[] q = { 0 };
                    Integer r[][] = { { 0 } };
                    for(int i = 0, j = 0;;);
                }
            }
        """)
    }
}
