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

    @Test
    fun nestedIfsWithNoBlock() {
        assertUnchangedByRefactoring(SimplifyBooleanReturn(), """
            public class A {
                public boolean absurdEquals(Object o) {
                    if(this == o)
                        if(this == 0) 
                            return true;
                    return false;
                }
            }
        """)
    }

    @Test
    fun dontAlterWhenElseIfPresent() {
        assertUnchangedByRefactoring(SimplifyBooleanReturn(), """
            public class A {
                public boolean foo(int n) {
                    if (n == 1) {
                        return false;
                    } 
                    else if (n == 2) {
                        return true;
                    } 
                    else {
                        return false;
                    }
                }
            }
        """)
    }

    @Test
    fun onlySimplifyToReturnWhenLastStatement() {
        assertUnchangedByRefactoring(SimplifyBooleanReturn(), """
            import java.util.*;
            public class A {
                public static boolean deepEquals(List<byte[]> l, List<byte[]> r) {
                    for (int i = 0; i < l.size(); ++i) {
                        if (!Arrays.equals(l.get(i), r.get(i))) {
                            return false;
                        }
                    }
                    return true;
                }
            }
        """)
    }
}
