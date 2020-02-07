package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.Token.*
import org.junit.jupiter.api.Test

open class EmptyBlockTest : Parser by OpenJdkParser() {
    @Test
    fun emptyTry() {
        val a = parse("""
            import java.io.*;

            public class A {
                {
                    try(FileInputStream fis = new FileInputStream("")) {
                        
                    } catch (IOException e) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyBlock.builder()
                .tokens(setOf(LITERAL_TRY))
                .build()).fix()

        assertRefactored(fixed, """
            public class A {
                {
                }
            }
        """)
    }

    @Test
    fun emptyCatchBlockWithIOException() {
        val a = parse("""
            import java.nio.file.*;
            import java.io.IOException;
            
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
                .tokens(setOf(LITERAL_CATCH))
                .build()).fix()

        assertRefactored(fixed, """
            import java.nio.file.*;
            import java.io.IOException;
            import java.io.UncheckedIOException;
            
            public class A {
                public void foo() {
                    try {
                        Files.readString(Path.of("somewhere"));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        """)
    }

    @Test
    fun emptyCatchBlockWithExceptionAndEmptyFinally() {
        val a = parse("""
            import java.nio.file.*;
            
            public class A {
                public void foo() {
                    try {
                        Files.readString(Path.of("somewhere"));
                    } catch (Throwable t) {
                    } finally {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyBlock.builder()
                .tokens(setOf(LITERAL_CATCH, LITERAL_FINALLY))
                .build()).fix()

        assertRefactored(fixed, """
            import java.nio.file.*;
            
            public class A {
                public void foo() {
                    try {
                        Files.readString(Path.of("somewhere"));
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                }
            }
        """)
    }

    @Test
    fun emptyLoops() {
        val a = parse("""
            public class A {
                public void foo() {
                    while(true) {
                    }
                    do {
                    } while(true);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyBlock.builder()
                .tokens(setOf(LITERAL_WHILE, LITERAL_DO))
                .build()).fix()

        assertRefactored(fixed, """
            public class A {
                public void foo() {
                    while(true) {
                        continue;
                    }
                    do {
                        continue;
                    } while(true);
                }
            }
        """)
    }

    @Test
    fun emptyStaticInit() {
        val a = parse("""
            public class A {
                static {}
                {}
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyBlock.builder()
                .tokens(setOf(STATIC_INIT))
                .build()).fix()

        assertRefactored(fixed, """
            public class A {
                {}
            }
        """)
    }

    @Test
    fun emptyInstanceInit() {
        val a = parse("""
            public class A {
                static {}
                {}
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyBlock.builder()
                .tokens(setOf(INSTANCE_INIT))
                .build()).fix()

        assertRefactored(fixed, """
            public class A {
                static {}
            }
        """)
    }

    @Test
    fun extractSideEffectsFromEmptyIfsWithNoElse() {
        val a = parse("""
            public class A {
                int n = sideEffect();
            
                int sideEffect() {
                    return new java.util.Random().nextInt();
                }
            
                boolean boolSideEffect() {
                    return sideEffect() == 0;
                }
            
                public void lotsOfIfs() {
                    if(sideEffect() == 1) {}
                    if(sideEffect() == sideEffect()) {}
                    int n;
                    if((n = sideEffect()) == 1) {}
                    if((n /= sideEffect()) == 1) {}
                    if(new A().n == 1) {}
                    if(!boolSideEffect()) {}
                    if(1 == 2) {}
                }
            }
        """.trimIndent())

        val fixed = a.refactor().run(EmptyBlock.builder()
                .tokens(setOf(STATIC_INIT))
                .build()).fix()

        assertRefactored(fixed, """
            public class A {
                int n = sideEffect();
            
                int sideEffect() {
                    return new java.util.Random().nextInt();
                }
            
                boolean boolSideEffect() {
                    return sideEffect() == 0;
                }
            
                public void lotsOfIfs() {
                    sideEffect();
                    sideEffect();
                    sideEffect();
                    int n;
                    n = sideEffect();
                    n /= sideEffect();
                    new A();
                    boolSideEffect();
                }
            }
        """)
    }
}
