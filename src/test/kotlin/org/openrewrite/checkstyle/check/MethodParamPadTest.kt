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
import org.openrewrite.config.MapConfigSource
import org.openrewrite.config.MapConfigSource.mapConfig
import org.openrewrite.java.JavaParser

open class MethodParamPadTest: JavaParser() {
    private val defaultConfig = emptyModule("MethodParamPad")
    
    @Test
    fun noSpaceInitializerPadding() {
        val a = parse("""
            public class A extends B {
                A () {
                    super ();
                }
            
                void foo (int n) {
                    A a = new A ();
                    foo (0);
                }
            }
            
            class B {}
            
            enum E {
                E1 ()
            }
        """.trimIndent())

        val fixed = a.refactor().visit(MethodParamPad.configure(defaultConfig)).fix().fixed

        assertRefactored(fixed, """
            public class A extends B {
                A() {
                    super();
                }
            
                void foo(int n) {
                    A a = new A();
                    foo(0);
                }
            }
            
            class B {}
            
            enum E {
                E1()
            }
        """)
    }

    @Test
    fun spaceInitializerPadding() {
        val a = parse("""
            public class A extends B {
                A() {
                    super();
                }
            
                void foo(int n) {
                    A a = new A();
                    foo(0);
                }
            }
            
            class B {}
            
            enum E {
                E1()
            }
        """.trimIndent())

        val fixed = a.refactor().visit(MethodParamPad.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="MethodParamPad">
                                <property name="option" value="SPACE"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            public class A extends B {
                A () {
                    super ();
                }
            
                void foo (int n) {
                    A a = new A ();
                    foo (0);
                }
            }
            
            class B {}
            
            enum E {
                E1 ()
            }
        """)
    }

    @Test
    fun allowLineBreaks() {
        val a = parse("""
            public class A extends B {
                void foo
                    (int n) {}
            }
        """.trimIndent())

        val fixed = a.refactor().visit(MethodParamPad.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="MethodParamPad">
                                <property name="allowLineBreaks" value="true"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            public class A extends B {
                void foo
                    (int n) {}
            }
        """)
    }
}
