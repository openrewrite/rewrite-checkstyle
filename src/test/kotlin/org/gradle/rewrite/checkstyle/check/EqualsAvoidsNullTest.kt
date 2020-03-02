package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class EqualsAvoidsNullTest: JavaParser() {
    @Test
    fun invertConditional() {
        val a = parse("""
            public class A {
                {
                    String s = null;
                    if(s.equals("test")) {}
                    if(s.equalsIgnoreCase("test")) {}
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EqualsAvoidsNull(false)).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    String s = null;
                    if("test".equals(s)) {}
                    if("test".equalsIgnoreCase(s)) {}
                }
            }
        """)
    }

    @Test
    fun removeUnnecessaryNullCheckAndParens() {
        val a = parse("""
            public class A {
                {
                    String s = null;
                    if((s != null && s.equals("test"))) {}
                    if(s != null && s.equals("test")) {}
                    if(null != s && s.equals("test")) {}
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EqualsAvoidsNull(false)).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    String s = null;
                    if("test".equals(s)) {}
                    if("test".equals(s)) {}
                    if("test".equals(s)) {}
                }
            }
        """)
    }
}
