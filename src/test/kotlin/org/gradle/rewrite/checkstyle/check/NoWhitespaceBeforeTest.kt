package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.gradle.rewrite.checkstyle.policy.PunctuationToken.*
import org.junit.jupiter.api.Test

open class NoWhitespaceBeforeTest : JavaParser() {
    @Test
    fun noWhitespaceBefore() {
        val a = parse("""
            package a ;
            import java.util.* ;
            public abstract class A {
                int m ;
            
                {
                    int n = 0 ;
                    int n , o = 0 ;
                    n ++ ;
                    n -- ;
                    new A() 
                        .m = 2 ;
                    foo(1 , 2) .foo(3 , 4);
                    List <String > generic = new ArrayList < >() ;
                    var a = Function ::identity ;
                    for(int i = 0 , j = 0 ; i < 2 ; i++ , j++) ;
                    while(true) ;
                    do { } while(true) ;
                    for(String s : generic) ;
                }
                
                abstract A foo(int n , int m, int ... others) ;
            }
            
            interface B {
                void foo() ;
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NoWhitespaceBefore.builder()
                .tokens(setOf(COMMA, SEMI, POST_INC, POST_DEC, DOT, GENERIC_START,
                        GENERIC_END, ELLIPSIS, METHOD_REF))
                .build()).fix().fixed

        assertRefactored(fixed, """
            package a;
            import java.util.*;
            public abstract class A {
                int m;
            
                {
                    int n = 0;
                    int n, o = 0;
                    n++;
                    n--;
                    new A().m = 2;
                    foo(1, 2).foo(3, 4);
                    List<String> generic = new ArrayList<>();
                    var a = Function::identity;
                    for(int i = 0, j = 0; i < 2; i++, j++);
                    while(true);
                    do { } while(true);
                    for(String s : generic);
                }
                
                abstract A foo(int n, int m, int... others);
            }
            
            interface B {
                void foo();
            }
        """)
    }

    @Test
    fun allowLinebreaks() {
        val a = parse("""
            public class A {
                int m;
            
                {
                    new A()
                        .m = 2;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NoWhitespaceBefore.builder()
                .tokens(setOf(DOT))
                .allowLineBreaks(true)
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int m;
            
                {
                    new A()
                        .m = 2;
                }
            }
        """)
    }

    @Test
    fun dontStripLastParameterSuffixInMethodDeclaration() {
        val aSource = """
            package a;
            public abstract class A {
                abstract A foo(
                    int n,
                    int m
                );
            }
        """.trimIndent()

        val a = parse(aSource)

        val fixed = a.refactor().visit(NoWhitespaceBefore.builder().build()).fix().fixed

        assertRefactored(fixed, aSource)
    }

    @Test
    fun dontStripLastArgumentSuffixInMethodInvocation() {
        val aSource = """
            package a;
            public class A {
                {
                    int n = Math.min(
                        1,
                        2
                    );
                }
            }
        """.trimIndent()

        val a = parse(aSource)

        val fixed = a.refactor().visit(NoWhitespaceBefore.builder().build()).fix().fixed

        assertRefactored(fixed, aSource)
    }

    @Test
    fun dontStripChainedMethodInvocationsByDefault() {
        val aSource = """
            package a;
            public class A {
                public static A a(int... n) { return new A(); }
            
                {
                    A
                        .a()
                        .a(
                            1,
                            2
                        );
                }
            }
        """.trimIndent()

        val a = parse(aSource)

        val fixed = a.refactor().visit(NoWhitespaceBefore.builder().build()).fix().fixed

        assertRefactored(fixed, aSource)
    }

    @Test
    fun dontStripStatementSuffixInTernaryConditionAndTrue() {
        val aSource = """
            package a;
            import java.util.*;
            public class A {
                List l;
                {
                    int n = l.isEmpty() ? l.size() : 2;
                }
            }
        """.trimIndent()

        val a = parse(aSource)

        val fixed = a.refactor().visit(NoWhitespaceBefore.builder().build()).fix().fixed

        assertRefactored(fixed, aSource)
    }

    @Test
    fun dontStripStatementSuffixPrecedingInstanceof() {
        val aSource = """
            package a;
            import java.util.*;
            public class A {
                List l;
                {
                    boolean b = l.subList(0, 1) instanceof ArrayList;
                }
            }
        """.trimIndent()

        val a = parse(aSource)

        val fixed = a.refactor().visit(NoWhitespaceBefore.builder().build()).fix().fixed

        assertRefactored(fixed, aSource)
    }
}
