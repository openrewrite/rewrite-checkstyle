package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class ExplicitInitializationTest : JavaParser() {
    @Test
    fun removeExplicitInitialization() {
        val a = parse("""
            class Test {
                private int a = 0;
                private long b = 0L;
                private short c = 0;
                private int d = 1;
                private long e = 2L;
                private int f;
                private char g = '\0';

                private boolean g = false;
                private boolean h = true;

                private Object i = new Object();
                private Object j = null;

                int k[] = null;
                int l[] = new int[0];
                
                private final Long l = null;
            }
        """.trimIndent())

        val fixed = a.refactor().visit(ExplicitInitialization()).fix().fixed

        assertRefactored(fixed, """
            class Test {
                private int a;
                private long b;
                private short c;
                private int d = 1;
                private long e = 2L;
                private int f;
                private char g;
            
                private boolean g;
                private boolean h = true;
            
                private Object i = new Object();
                private Object j;
            
                int k[];
                int l[] = new int[0];
                
                private final Long l = null;
            }
        """)
    }
}
