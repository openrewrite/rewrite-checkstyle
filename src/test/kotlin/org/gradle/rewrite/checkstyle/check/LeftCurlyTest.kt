package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.gradle.rewrite.checkstyle.policy.LeftCurlyPolicy
import org.junit.jupiter.api.Test

open class LeftCurlyTest : JavaParser() {
    @Test
    fun eol() {
        val a = parse("""
            class A
            {
                {
                    if(1 == 2)
                    {
                    }
                }
            
                private static final int N = 1;
                static
                {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(LeftCurly.builder().build()).fix().fixed

        assertRefactored(fixed, """
            class A {
                {
                    if(1 == 2) {
                    }
                }
            
                private static final int N = 1;
                static {
                }
            }
        """)
    }

    @Test
    fun nl() {
        val a = parse("""
            class A {
                {
                    if(1 == 2) {
                    }
                }
            
                private static final int N = 1;
                static {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(LeftCurly.builder()
                .option(LeftCurlyPolicy.NL)
                .build()).fix().fixed

        assertRefactored(fixed, """
            class A
            {
                {
                    if(1 == 2)
                    {
                    }
                }
            
                private static final int N = 1;
                static
                {
                }
            }
        """)
    }

    @Test
    fun nlow() {
        val a = parse("""
            class A {
                {
                    if(1 == 2)
                    {
                    }
                    if(1 == 2 &&
                        3 == 4) {
                    }
                }
            
                private static final int N = 1;
                static {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(LeftCurly.builder()
                .option(LeftCurlyPolicy.NLOW)
                .build()).fix().fixed

        assertRefactored(fixed, """
            class A {
                {
                    if(1 == 2) {
                    }
                    if(1 == 2 &&
                        3 == 4)
                    {
                    }
                }
            
                private static final int N = 1;
                static
                {
                }
            }
        """)
    }

    @Test
    fun ignoreEnums() {
        val a = parse("""
            class A {
                {
                    switch(1) {
                    case 1:
                    {
                    }
                    case 2: {
                    }
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(LeftCurly.builder()
                .option(LeftCurlyPolicy.EOL)
                .build()).fix().fixed

        assertRefactored(fixed, """
            class A {
                {
                    switch(1) {
                    case 1:
                    {
                    }
                    case 2: {
                    }
                    }
                }
            }
        """)
    }
}
