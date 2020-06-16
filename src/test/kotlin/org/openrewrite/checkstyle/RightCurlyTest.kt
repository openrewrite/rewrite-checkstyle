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
import org.openrewrite.checkstyle.policy.RightCurlyPolicy

open class RightCurlyTest: CheckstyleRefactorVisitorTest(RightCurly::class) {
    @Test
    fun alone() {
        val a = jp.parse("""
            class A {
                {
                    if(1 == 2) {} else if(2 == 3) {} else {}
                    
                    try {} catch(Throwable t) {} finally {}
                    
                    { int n = 1; }
                }
                
                public int foo() { return 1; }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(configXml("tokens" to "LITERAL_TRY,LITERAL_CATCH,LITERAL_FINALLY,LITERAL_IF,LITERAL_ELSE,METHOD_DEF",
                "option" to RightCurlyPolicy.ALONE)).fix().fixed

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
        val a = jp.parse("""
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

        val fixed = a.refactor().visit(configXml("option" to RightCurlyPolicy.ALONE_OR_SINGLELINE))
                .fix().fixed

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
        val a = jp.parse("""
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

        val fixed = a.refactor().visit(configXml("option" to RightCurlyPolicy.SAME))
                .fix().fixed

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
