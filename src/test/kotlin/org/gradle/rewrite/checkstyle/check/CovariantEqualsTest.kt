package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class CovariantEqualsTest : Parser by OpenJdkParser() {
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

        val fixed = a.refactor().run(CovariantEquals()).fix()

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
