package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.PadPolicy
import org.junit.jupiter.api.Test

open class MethodParamPadTest: Parser by OpenJdkParser() {
    @Test
    fun noSpaceInitializerPadding() {
        val a = parse("""
            public class A extends B {
                A () {
                    super ();
                }
            
                void foo (int n) {
                    A a = new A ();
                    foo (0);
                }
            }
            
            class B {}
            
            enum E {
                E1 ()
            }
        """.trimIndent())

        val fixed = a.refactor().run(MethodParamPad.builder().build()).fix()

        assertRefactored(fixed, """
            public class A extends B {
                A() {
                    super();
                }
            
                void foo(int n) {
                    A a = new A();
                    foo(0);
                }
            }
            
            class B {}
            
            enum E {
                E1()
            }
        """)
    }

    @Test
    fun spaceInitializerPadding() {
        val a = parse("""
            public class A extends B {
                A() {
                    super();
                }
            
                void foo(int n) {
                    A a = new A();
                    foo(0);
                }
            }
            
            class B {}
            
            enum E {
                E1()
            }
        """.trimIndent())

        val fixed = a.refactor().run(MethodParamPad.builder().option(PadPolicy.SPACE).build()).fix()

        assertRefactored(fixed, """
            public class A extends B {
                A () {
                    super ();
                }
            
                void foo (int n) {
                    A a = new A ();
                    foo (0);
                }
            }
            
            class B {}
            
            enum E {
                E1 ()
            }
        """)
    }

    @Test
    fun allowLineBreaks() {
        val a = parse("""
            public class A extends B {
                void foo
                    (int n) {}
            }
        """.trimIndent())

        val fixed = a.refactor().run(MethodParamPad.builder()
                .allowLineBreaks(true).build()).fix()

        assertRefactored(fixed, """
            public class A extends B {
                void foo
                    (int n) {}
            }
        """)
    }
}
