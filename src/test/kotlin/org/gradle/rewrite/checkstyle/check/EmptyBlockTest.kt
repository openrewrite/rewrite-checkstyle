package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.Parser
import org.gradle.rewrite.checkstyle.policy.Token.*
import org.junit.jupiter.api.Test

open class EmptyBlockTest : Parser() {
    @Test
    fun emptySwitch() {
        val a = parse("""
            public class A {
                {
                    int i = 0;
                    switch(i) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_SWITCH))
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    int i = 0;
                }
            }
        """)
    }

    @Test
    fun emptySynchronized() {
        val a = parse("""
            public class A {
                {
                    final Object o = new Object();
                    synchronized(o) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_SYNCHRONIZED))
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    final Object o = new Object();
                }
            }
        """)
    }

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

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_TRY))
                .build()).fix().fixed

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
            import java.io.IOException;
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

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_CATCH))
                .build()).fix().fixed

        assertRefactored(fixed, """
            import java.io.IOException;
            import java.io.UncheckedIOException;
            import java.nio.file.*;
            
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

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_CATCH, LITERAL_FINALLY))
                .build()).fix().fixed

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

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_WHILE, LITERAL_DO))
                .build()).fix().fixed

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

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(STATIC_INIT))
                .build()).fix().fixed

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

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(INSTANCE_INIT))
                .build()).fix().fixed

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

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_IF))
                .build()).fix().fixed

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

    @Test
    fun invertIfWithOnlyElseClauseAndBinaryOperator() {
        // extra spaces after the original if condition to ensure that we preserve the if statement's block formatting
        val a = parse("""
            public class A {
                {
                    if("foo".length() > 3)   {
                    } else {
                        System.out.println("this");
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_IF))
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    if("foo".length() <= 3)   {
                        System.out.println("this");
                    }
                }
            }
        """)
    }

    @Test
    fun invertIfWithElseIfElseClause() {
        val a = parse("""
            public class A {
                {
                    if("foo".length() > 3) {
                    } else if("foo".length() > 4) {
                        System.out.println("longer");
                    }
                    else {
                        System.out.println("this");
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EmptyBlock.builder()
                .tokens(setOf(LITERAL_IF))
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    if("foo".length() <= 3) {
                        if("foo".length() > 4) {
                            System.out.println("longer");
                        }
                        else {
                            System.out.println("this");
                        }
                    }
                }
            }
        """)
    }
}
