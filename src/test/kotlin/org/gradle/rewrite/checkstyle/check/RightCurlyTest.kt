package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.RightCurlyPolicy
import org.gradle.rewrite.checkstyle.policy.Token.*
import org.junit.jupiter.api.Test

open class RightCurlyTest : Parser by OpenJdkParser() {
    @Test
    fun alone() {
        val a = parse("""
            class A {
                {
                    if(1 == 2) {} else if(2 == 3) {} else {}
                    
                    try {} catch(Throwable t) {} finally {}
                    
                    { int n = 1; }
                }
                
                public int foo() { return 1; }
            }
        """.trimIndent())

        val fixed = a.refactor().run(RightCurly.builder()
                .tokens(setOf(LITERAL_TRY, LITERAL_CATCH, LITERAL_FINALLY, LITERAL_IF, LITERAL_ELSE, METHOD_DEF))
                .build()).fix()

        assertRefactored(fixed, """
            class A {
                {
                    if(1 == 2) {
                    }
                    else if(2 == 3) {
                    }
                    else {
                    }
                    
                    try {
                    }
                    catch(Throwable t) {
                    }
                    finally {
                    }
                    
                    {
                        int n = 1;
                    }
                }
                
                public int foo() {
                    return 1;
                }
            }
        """)
    }

    @Test
    fun aloneOrSingleline() {
        val a = parse("""
            class A {
                {
                    if(1 == 2) {} else if(2 == 3) {} else {}
                    
                    try {} catch(Throwable t) {} finally {}
                    
                    {
                        int n = 1; }
                }
                
                public int foo() { return 1; }
            }
        """.trimIndent())

        val fixed = a.refactor().run(RightCurly.builder()
                .option(RightCurlyPolicy.ALONE_OR_SINGLELINE)
                .build()).fix()

        assertRefactored(fixed, """
            class A {
                {
                    if(1 == 2) {}
                    else if(2 == 3) {}
                    else {}
                    
                    try {}
                    catch(Throwable t) {}
                    finally {}
                    
                    {
                        int n = 1;
                    }
                }
                
                public int foo() { return 1; }
            }
        """)
    }

    @Test
    fun same() {
        val a = parse("""
            class A {
                {
                    if(1 == 2) {} else if(2 == 3) {}
                    else {}
                    
                    try {} catch(java.io.IOException e) {}
                    catch(Throwable t) {}
                    finally {}
                    
                    {
                        int n = 1; }
                }
                
                public int foo() { return 1; }
            }
        """.trimIndent())

        val fixed = a.refactor().run(RightCurly.builder()
                .option(RightCurlyPolicy.SAME)
                .build()).fix()

        assertRefactored(fixed, """
            class A {
                {
                    if(1 == 2) {} else if(2 == 3) {} else {}
                    
                    try {} catch(java.io.IOException e) {} catch(Throwable t) {} finally {}
                    
                    {
                        int n = 1;
                    }
                }
                
                public int foo() { return 1; }
            }
        """)
    }
}