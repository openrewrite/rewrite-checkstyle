package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class StringLiteralEqualityTest : Parser by OpenJdkParser() {
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

        val fixed = a.refactor().run(StringLiteralEquality()).fix()

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
