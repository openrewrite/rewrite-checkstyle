package org.gradle.rewrite.checkstyle.check

import org.openrewrite.Parser
import org.junit.jupiter.api.Test

open class DefaultComesLastTest : Parser() {
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
}
