package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class StringLiteralEqualityTest : JavaParser() {
    @Test
    fun stringLiteralEqualityReplacedWithEquals() {
        val a = parse("""
            class Test {
                String a;
                {
                    if(a == "test");
                    if("test" == a);
                    if("test" == "test");
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(StringLiteralEquality()).fix().fixed

        assertRefactored(fixed, """
            class Test {
                String a;
                {
                    if("test".equals(a));
                    if("test".equals(a));
                    if("test".equals("test"));
                }
            }
        """)
    }
}
