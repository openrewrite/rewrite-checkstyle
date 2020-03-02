package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class EmptyStatementTest: JavaParser() {
    @Test
    fun removeEmptyStatement() {
        val a = parse("""
            public class A {
                {
                    if(1 == 2);
                        System.out.println("always runs");
                    for(;;);
                        System.out.println("always runs");
                    for(String s : new String[0]);
                        System.out.println("always runs");
                    while(true);
                        System.out.println("always runs");
                    while(true);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyStatement()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    if(1 == 2)
                        System.out.println("always runs");
                    for(;;)
                        System.out.println("always runs");
                    for(String s : new String[0])
                        System.out.println("always runs");
                    while(true)
                        System.out.println("always runs");
                }
            }
        """)
    }
}
