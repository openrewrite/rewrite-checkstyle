package org.gradle.rewrite.checkstyle.check

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

open class SimplifyBooleanReturnTest : JavaParser() {
    @Test
    fun simplifyBooleanReturn() {
        val a = parse("""
            public class A {
                boolean ifNoElse() {
                    if (isOddMillis()) {
                        return true;
                    }
                    return false;
                }
                
                static boolean isOddMillis() {
                    boolean even = System.currentTimeMillis() % 2 == 0;
                    if (even == true) {
                        return false;
                    }
                    else {
                        return true;
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(SimplifyBooleanReturn()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                boolean ifNoElse() {
                    return isOddMillis();
                }
                
                static boolean isOddMillis() {
                    boolean even = System.currentTimeMillis() % 2 == 0;
                    return !(even == true);
                }
            }
        """)
    }

    @Test
    fun dontSimplifyToReturnUnlessLastStatement() {
        val a = parse("""
            public class A {
                public boolean absurdEquals(Object o) {
                    if(this == o) {
                        return true;
                    }
                    if(this == o) {
                        return true;
                    }
                    return false;
                } 
            }
        """.trimIndent())

        val fixed = a.refactor().visit(SimplifyBooleanReturn()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                public boolean absurdEquals(Object o) {
                    if(this == o) {
                        return true;
                    }
                    return this == o;
                } 
            }""")
    }
}
