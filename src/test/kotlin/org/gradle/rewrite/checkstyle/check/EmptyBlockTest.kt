package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.LiteralToken
import org.junit.jupiter.api.Test

open class EmptyBlockTest : Parser by OpenJdkParser() {
    @Test
    fun emptyCatchBlock() {
        val a = parse("""
            import java.nio.file.*;
            
            public class A {
                public void foo() {
                    try {
                        Files.readString(Path.of("somewhere"));
                    } catch (IOException e) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyBlock.builder()
                .tokens(setOf(LiteralToken.LITERAL_CATCH))
                .build()).fix()

        assertRefactored(fixed, """
            import java.nio.file.*;
            
            public class A {
                public void foo() {
                    try {
                        Files.readString(Path.of("somewhere"));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        """)
    }
}
