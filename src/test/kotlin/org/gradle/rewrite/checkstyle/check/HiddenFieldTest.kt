package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class HiddenFieldTest : Parser by OpenJdkParser() {
    @Test
    fun renameHiddenFields() {
        val a = parse("""
            public class A {
                int n;
                int n1;

                public void foo(int n) {
                    int n1 = 2;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(HiddenField.builder().build()).fix()

        assertRefactored(fixed, """
            public class A {
                int n;
                int n1;

                public void foo(int n2) {
                    int n3 = 2;
                }
            }
        """)
    }
}
