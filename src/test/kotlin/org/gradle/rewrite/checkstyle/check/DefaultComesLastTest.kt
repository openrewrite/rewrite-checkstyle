package org.gradle.rewrite.checkstyle.check

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

open class DefaultComesLastTest : JavaParser() {
    @Test
    fun moveDefaultToLastAlongWithItsStatementsAndAddBreakIfNecessary() {
        val a = parse("""
            class Test {
                int n;
                {
                    switch (n) {
                        case 1:
                            break;
                        case 2:
                            break;
                        default:
                            System.out.println("default");
                            break;
                        case 3:
                            System.out.println("case3");
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(DefaultComesLast()).fix().fixed

        assertRefactored(fixed, """
            class Test {
                int n;
                {
                    switch (n) {
                        case 1:
                            break;
                        case 2:
                            break;
                        case 3:
                            System.out.println("case3");
                            break;
                        default:
                            System.out.println("default");
                    }
                }
            }
        """)
    }

    @Test
    fun moveDefaultToLastWhenSharedWithAnotherCaseStatement() {
        val a = parse("""
            class Test {
                int n;
                {
                    switch (n) {
                        case 1:
                            break;
                        case 2:
                            break;
                        case 3:
                        default:
                            break;
                        case 4:
                        case 5:
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(DefaultComesLast()).fix().fixed

        assertRefactored(fixed, """
            class Test {
                int n;
                {
                    switch (n) {
                        case 1:
                            break;
                        case 2:
                            break;
                        case 4:
                        case 5:
                            break;
                        case 3:
                        default:
                    }
                }
            }
        """)
    }

    @Test
    fun skipIfLastAndSharedWithCase() {
        val a = parse("""
            class Test {
                int n;
                {
                    switch (n) {
                        case 1:
                            break;
                        case 2:
                        default:
                            break;
                        case 3:
                            break;
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(DefaultComesLast(true)).fix().fixed

        assertRefactored(fixed, """
            class Test {
                int n;
                {
                    switch (n) {
                        case 1:
                            break;
                        case 2:
                        default:
                            break;
                        case 3:
                            break;
                    }
                }
            }
        """)
    }

    @Test
    fun defaultIsLastAndThrows() {
        assertUnchangedByRefactoring(DefaultComesLast(), """
            class Test {
                int n;
                {
                    switch (n) {
                        case 1:
                            break;
                        default:
                            throw new RuntimeException("unexpected value");
                    }
                }
            }
        """)
    }

    @Test
    fun defaultIsLastAndReturnsNonVoid() {
        assertUnchangedByRefactoring(DefaultComesLast(), """
            class Test {
                public int foo(int n) {
                    switch (n) {
                        case 1:
                            return 1;
                        default:
                            return 2;
                    }
                }
            }
        """)
    }

    @Test
    fun dontAddBreaksIfCasesArentMoving() {
        assertUnchangedByRefactoring(DefaultComesLast(), """
            class Test {
                int n;
                boolean foo() {
                    switch (n) {
                        case 1:
                        case 2:
                            System.out.println("side effect");
                        default:
                            return true;
                    }
                }
            }
        """)
    }

    @Test
    fun dontRemoveExtraneousDefaultCaseBreaks() {
        assertUnchangedByRefactoring(DefaultComesLast(), """
            class Test {
                int n;
                void foo() {
                    switch (n) {
                        default:
                            break;
                    }
                }
            }
        """)
    }

    @Test
    fun allCasesGroupedWithDefault() {
        assertUnchangedByRefactoring(DefaultComesLast(), """
            class Test {
                int n;
                boolean foo() {
                    switch (n) {
                        case 1:
                        case 2:
                        default:
                            return true;
                    }
                }
            }
        """)
    }
}
