package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.gradle.rewrite.checkstyle.policy.Token.LITERAL_CASE
import org.gradle.rewrite.checkstyle.policy.Token.LITERAL_DEFAULT
import org.junit.jupiter.api.Test

open class NeedBracesTest : JavaParser() {
    @Test
    fun addBraces() {
        val a = parse("""
            public class A {
                int n;
                void foo() {
                    while (true);
                    if (n == 1) return;
                    else return;
                    while (true) return;
                    do this.notify(); while (true);
                    for (int i = 0; ; ) this.notify();
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.builder().build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                void foo() {
                    while (true) {
                    }
                    if (n == 1) {
                        return;
                    }
                    else {
                        return;
                    }
                    while (true) {
                        return;
                    }
                    do {
                        this.notify();
                    } while (true);
                    for (int i = 0; ; ) {
                        this.notify();
                    }
                }
            }
        """)
    }

    @Test
    fun allowEmptyLoopBody() {
        val a = parse("""
            public class A {
                {
                    while (true);
                    for(int i = 0; i < 10; i++);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.builder()
                .allowEmptyLoopBody(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    while (true);
                    for(int i = 0; i < 10; i++);
                }
            }
        """)
    }

    @Test
    fun allowSingleLineStatement() {
        val a = parse("""
            public class A {
                int n;
                void foo() {
                    if (n == 1) return;
                    while (true) return;
                    do this.notify(); while (true);
                    for (int i = 0; ; ) this.notify();
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.builder()
                .allowSingleLineStatement(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                void foo() {
                    if (n == 1) return;
                    while (true) return;
                    do this.notify(); while (true);
                    for (int i = 0; ; ) this.notify();
                }
            }
        """)
    }

    @Test
    fun allowSingleLineStatementInSwitch() {
        val a = parse("""
            public class A {
                {
                    int n = 1;
                    switch (n) {
                      case 1: counter++; break;
                      case 6: counter += 10; break;
                      default: counter = 100; break;
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.builder()
                .tokens(setOf(LITERAL_CASE, LITERAL_DEFAULT))
                .allowSingleLineStatement(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    int n = 1;
                    switch (n) {
                      case 1: counter++; break;
                      case 6: counter += 10; break;
                      default: counter = 100; break;
                    }
                }
            }
        """)
    }
}
