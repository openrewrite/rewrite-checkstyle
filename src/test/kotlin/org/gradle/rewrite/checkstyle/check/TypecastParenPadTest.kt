package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.PadPolicy
import org.junit.jupiter.api.Test

open class TypecastParenPadTest : Parser by OpenJdkParser() {
    private val padded = """
        public class A {
            { 
                long m = 0L;
                int n = ( int ) m;
            }
        }
    """.trimIndent()

    private val unpadded = """
        public class A {
            { 
                long m = 0L;
                int n = (int) m;
            }
        }
    """.trimIndent()

    @Test
    fun shouldPadTypecast() {
        val fixed = parse(unpadded).refactor().visit(TypecastParenPad(PadPolicy.SPACE)).fix().fixed
        assertRefactored(fixed, padded)
    }

    @Test
    fun shouldUnpadTypecast() {
        val fixed = parse(padded).refactor().visit(TypecastParenPad()).fix().fixed
        assertRefactored(fixed, unpadded)
    }
}
