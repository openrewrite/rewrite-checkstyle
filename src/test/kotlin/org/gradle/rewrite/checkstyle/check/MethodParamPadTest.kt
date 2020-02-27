package org.gradle.rewrite.checkstyle.check

import org.openrewrite.Parser
import org.gradle.rewrite.checkstyle.policy.PadPolicy
import org.junit.jupiter.api.Test

open class MethodParamPadTest: Parser() {
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

        val fixed = a.refactor().visit(MethodParamPad.builder().build()).fix().fixed

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

        val fixed = a.refactor().visit(MethodParamPad.builder().option(PadPolicy.SPACE).build()).fix().fixed

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

        val fixed = a.refactor().visit(MethodParamPad.builder()
                .allowLineBreaks(true).build()).fix().fixed

        assertRefactored(fixed, """
            public class A extends B {
                void foo
                    (int n) {}
            }
        """)
    }
}
