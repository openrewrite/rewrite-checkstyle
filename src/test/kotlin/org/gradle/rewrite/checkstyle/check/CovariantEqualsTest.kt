package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.Parser
import org.junit.jupiter.api.Test

open class CovariantEqualsTest : Parser() {
    @Test
    fun replaceWithNonCovariantEquals() {
        val a = parse("""
            class Test {
                int n;
                
                public boolean equals(Test t) {
                    return n == t.n;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(CovariantEquals()).fix().fixed

        assertRefactored(fixed, """
            class Test {
                int n;
                
                @Override
                public boolean equals(Object o) {
                    if (this == o) return true;
                    if (o == null || getClass() != o.getClass()) return false;
                    Test t = (Test) o;
                    return n == t.n;
                }
            }
        """)
    }
}
