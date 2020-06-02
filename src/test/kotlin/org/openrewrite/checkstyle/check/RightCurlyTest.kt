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
package org.openrewrite.checkstyle.check

import org.junit.jupiter.api.Test
import org.openrewrite.config.MapConfigSource.mapConfig
import org.openrewrite.java.JavaParser

open class RightCurlyTest : JavaParser() {
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

        val fixed = a.refactor().visit(RightCurly.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="RightCurly">
                                <property name="tokens" value="LITERAL_TRY,LITERAL_CATCH,LITERAL_FINALLY,LITERAL_IF,LITERAL_ELSE,METHOD_DEF"/>
                                <property name="option" value="alone"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

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

        val fixed = a.refactor().visit(RightCurly.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="RightCurly">
                                <property name="option" value="ALONE_OR_SINGLELINE"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

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

        val fixed = a.refactor().visit(RightCurly.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="RightCurly">
                                <property name="option" value="SAME"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

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
