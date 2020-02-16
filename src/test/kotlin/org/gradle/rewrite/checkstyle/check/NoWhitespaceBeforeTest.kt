package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.PunctuationToken.*
import org.junit.jupiter.api.Test

open class NoWhitespaceBeforeTest : Parser by OpenJdkParser() {
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

        val fixed = a.refactor().run(NoWhitespaceBefore.builder()
                .tokens(setOf(COMMA, SEMI, POST_INC, POST_DEC, DOT, GENERIC_START,
                        GENERIC_END, ELLIPSIS, METHOD_REF))
                .build()).fix()

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

        val fixed = a.refactor().run(NoWhitespaceBefore.builder()
                .tokens(setOf(DOT))
                .allowLineBreaks(true)
                .build()).fix()

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
}
