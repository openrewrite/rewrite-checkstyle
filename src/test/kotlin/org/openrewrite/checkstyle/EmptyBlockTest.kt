/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.checkstyle

import org.junit.jupiter.api.Test

open class EmptyBlockTest : CheckstyleRefactorVisitorTest(EmptyBlock::class) {
    private fun emptyBlock(vararg tokens: String) = configXml("tokens" to tokens.joinToString(","))

    @Test
    fun emptySwitch() {
        val a = jp.parse("""
            public class A {
                {
                    int i = 0;
                    switch(i) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(emptyBlock("LITERAL_SWITCH"))
                .fix().fixed

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
        val a = jp.parse("""
            public class A {
                {
                    final Object o = new Object();
                    synchronized(o) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(emptyBlock("LITERAL_SYNCHRONIZED"))
                .fix().fixed

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
        val a = jp.parse("""
            import java.io.*;

            public class A {
                {
                    try(FileInputStream fis = new FileInputStream("")) {
                        
                    } catch (IOException e) {
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(emptyBlock("LITERAL_TRY"))
                .fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                }
            }
        """)
    }

    @Test
    fun emptyCatchBlockWithIOException() {
        val a = jp.parse("""
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

        val fixed = a.refactor().visit(emptyBlock("LITERAL_CATCH"))
                .fix().fixed

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
        val a = jp.parse("""
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

        val fixed = a.refactor().visit(emptyBlock("LITERAL_CATCH", "LITERAL_FINALLY"))
                .fix().fixed

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
        val a = jp.parse("""
            public class A {
                public void foo() {
                    while(true) {
                    }
                    do {
                    } while(true);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(emptyBlock("LITERAL_WHILE", "LITERAL_DO"))
                .fix().fixed

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
        val a = jp.parse("""
            public class A {
                static {}
                {}
            }
        """.trimIndent())

        val fixed = a.refactor().visit(emptyBlock("STATIC_INIT"))
                .fix().fixed

        assertRefactored(fixed, """
            public class A {
                {}
            }
        """)
    }

    @Test
    fun emptyInstanceInit() {
        val a = jp.parse("""
            public class A {
                static {}
                {}
            }
        """.trimIndent())

        val fixed = a.refactor().visit(emptyBlock("INSTANCE_INIT"))
                .fix().fixed

        assertRefactored(fixed, """
            public class A {
                static {}
            }
        """)
    }

    @Test
    fun extractSideEffectsFromEmptyIfsWithNoElse() {
        val a = jp.parse("""
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

        val fixed = a.refactor().visit(emptyBlock("LITERAL_IF"))
                .fix().fixed

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
        val a = jp.parse("""
            public class A {
                {
                    if("foo".length() > 3)   {
                    } else {
                        System.out.println("this");
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(emptyBlock("LITERAL_IF"))
                .fix().fixed

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
        val a = jp.parse("""
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

        val fixed = a.refactor().visit(emptyBlock("LITERAL_IF"))
                .fix(1).fixed

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
