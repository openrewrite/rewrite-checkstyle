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

open class NeedBracesTest : JavaParser() {
    private val defaultConfig = emptyModule("NeedBraces")
    
    @Test
    fun addBraces() {
        val a = parse("""
            public class A {
                int n;
                void foo() {
                    while (true);
                    if (n == 1) return;
                    else return;
                    while (true) return;
                    do this.notify(); while (true);
                    for (int i = 0; ; ) this.notify();
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.configure(defaultConfig)).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                void foo() {
                    while (true) {
                    }
                    if (n == 1) {
                        return;
                    }
                    else {
                        return;
                    }
                    while (true) {
                        return;
                    }
                    do {
                        this.notify();
                    } while (true);
                    for (int i = 0; ; ) {
                        this.notify();
                    }
                }
            }
        """)
    }

    @Test
    fun allowEmptyLoopBody() {
        val a = parse("""
            public class A {
                {
                    while (true);
                    for(int i = 0; i < 10; i++);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="NeedBraces">
                                <property name="allowEmptyLoopBody" value="true"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    while (true);
                    for(int i = 0; i < 10; i++);
                }
            }
        """)
    }

    @Test
    fun allowSingleLineStatement() {
        val a = parse("""
            public class A {
                int n;
                void foo() {
                    if (n == 1) return;
                    while (true) return;
                    do this.notify(); while (true);
                    for (int i = 0; ; ) this.notify();
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="NeedBraces">
                                <property name="allowSingleLineStatement" value="true"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int n;
                void foo() {
                    if (n == 1) return;
                    while (true) return;
                    do this.notify(); while (true);
                    for (int i = 0; ; ) this.notify();
                }
            }
        """)
    }

    @Test
    fun allowSingleLineStatementInSwitch() {
        val a = parse("""
            public class A {
                {
                    int n = 1;
                    switch (n) {
                      case 1: counter++; break;
                      case 6: counter += 10; break;
                      default: counter = 100; break;
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NeedBraces.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="NeedBraces">
                                <property name="allowSingleLineStatements" value="true"/>
                                <property name="tokens" value="LITERAL_CASE,LITERAL_DEFAULT"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    int n = 1;
                    switch (n) {
                      case 1: counter++; break;
                      case 6: counter += 10; break;
                      default: counter = 100; break;
                    }
                }
            }
        """)
    }

    @Test
    fun dontSplitElseIf() {
        val aSource = """
            public class A {
                int n;
                {
                    if (n == 1) {
                    }
                    else if (n == 2) {
                    }
                    else {
                    }
                }
            }
        """.trimIndent()

        val a = parse(aSource)

        val fixed = a.refactor().visit(NeedBraces.configure(defaultConfig)).fix().fixed

        assertRefactored(fixed, aSource)
    }
}
