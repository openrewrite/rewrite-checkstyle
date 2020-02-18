package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class SimplifyBooleanReturnTest : Parser by OpenJdkParser() {
    @Test
    fun simplifyBooleanExpression() {
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

                    // can be simplified to "if (even)"
                    if (even == true) {
                        return false;
                    }
                    else {
                        return true;
                    }
                    // return can be simplified to "return !even"
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(SimplifyBooleanReturn()).fix()

        assertRefactored(fixed, """
            public class A {
                boolean ifNoElse() {
                    return isOddMillis();
                }
                
                static boolean isOddMillis() {
                    boolean even = System.currentTimeMillis() % 2 == 0;
                    return !even;
                }
            }
        """)
    }
}
