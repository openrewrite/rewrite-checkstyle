package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test
import java.util.regex.Pattern

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

        val fixed = a.refactor().visit(HiddenField.builder().build()).fix().fixed

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

    @Test
    fun ignorePattern() {
        val a = parse("""
            public class A {
                int n;
                
                public void foo(int n) {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(HiddenField.builder()
                .ignoreFormat(Pattern.compile("\\w+"))
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                
                public void foo(int n) {
                }
            }
        """)
    }

    @Test
    fun ignoreConstructorParameter() {
        val a = parse("""
            public class A {
                int n;
                
                A(int n) {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(HiddenField.builder()
                .ignoreConstructorParameter(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                
                A(int n) {
                }
            }
        """)
    }

    @Test
    fun ignoreSetter() {
        val a = parse("""
            public class A {
                int n;
                
                public void setN(int n) {
                }
                
                public A setN(int n) {
                    return this;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(HiddenField.builder()
                .ignoreSetter(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                
                public void setN(int n) {
                }
                
                public A setN(int n1) {
                    return this;
                }
            }
        """)
    }

    @Test
    fun ignoreSetterThatReturnsItsClass() {
        val a = parse("""
            public class A {
                int n;
                
                public A setN(int n) {
                    return this;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(HiddenField.builder()
                .ignoreSetter(true)
                .setterCanReturnItsClass(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                
                public A setN(int n) {
                    return this;
                }
            }
        """)
    }

    @Test
    fun ignoreAbstractMethods() {
        val a = parse("""
            public abstract class A {
                int n;
                
                public abstract void foo(int n);
            }
        """.trimIndent())

        val fixed = a.refactor().visit(HiddenField.builder()
                .ignoreAbstractMethods(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public abstract class A {
                int n;
                
                public abstract void foo(int n);
            }
        """)
    }
}
