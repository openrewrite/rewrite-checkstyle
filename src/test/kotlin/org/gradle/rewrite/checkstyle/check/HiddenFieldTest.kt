package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class HiddenFieldTest : Parser by OpenJdkParser() {
    @Test
    fun renameHiddenFields() {
        val b = """
            public class B {
                protected int n2;
                int n3;
                private int n4;
            }
        """.trimIndent()

        val a = parse("""
            public class A extends B {
                int n;
                int n1;

                class C {
                    public void foo(int n) {
                        int n1 = 2;
                    }
                }
                
                static class D {
                    public void foo(int n) {
                    }
                }
            }
        """.trimIndent(), b)

        val fixed = a.refactor().run(HiddenField.builder().build()).fix()

        assertRefactored(fixed, """
            public class A extends B {
                int n;
                int n1;

                class C {
                    public void foo(int n4) {
                        int n5 = 2;
                    }
                }
                
                static class D {
                    public void foo(int n) {
                    }
                }
            }
        """)
    }
}
