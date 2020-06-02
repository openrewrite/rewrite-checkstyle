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
import org.openrewrite.checkstyle.policy.OperatorToken
import org.openrewrite.config.MapConfigSource.mapConfig
import org.openrewrite.java.JavaParser

open class OperatorWrapTest : JavaParser() {
    @Test
    fun operatorOnNewline() {
        val a = parse("""
            import java.io.*;
            class A {
                {
                    String s = "aaa" +
                        "b" + "c";
                    if(s instanceof
                        String);
                    boolean b = s.contains("a") ?
                        false :
                        true;
                    s +=
                        "b";
                    var a = Function::
                        identity;
                    int n =
                        1;
                    int n[] =
                        new int[0];
                    n =
                        2;
                }
                
                <T extends Serializable &
                        Comparable> T foo() {
                    return null;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(OperatorWrap.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="OperatorWrap">
                                <property name="tokens" value="${OperatorToken.values().joinToString(",")}"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            import java.io.*;
            class A {
                {
                    String s = "aaa"
                        + "b" + "c";
                    if(s
                        instanceof String);
                    boolean b = s.contains("a")
                        ? false
                        : true;
                    s
                        += "b";
                    var a = Function
                        ::identity;
                    int n
                        = 1;
                    int n[]
                        = new int[0];
                    n
                        = 2;
                }
                
                <T extends Serializable
                        & Comparable> T foo() {
                    return null;
                }
            }
        """)
    }

    @Test
    fun operatorOnEndOfLine() {
        val a = parse("""
            import java.io.*;
            class A {
                {
                    String s = "aaa"
                        + "b" + "c";
                    if(s
                        instanceof String);
                    boolean b = s.contains("a")
                        ? false
                        : true;
                    s
                        += "b";
                    var a = Function
                        ::identity;
                    int n
                        = 1;
                    int n[]
                        = new int[0];
                    n
                        = 2;
                }
                
                <T extends Serializable
                        & Comparable> T foo() {
                    return null;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(OperatorWrap.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="OperatorWrap">
                                <property name="option" value="EOL"/>
                                <property name="tokens" value="${OperatorToken.values().joinToString(",")}"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            import java.io.*;
            class A {
                {
                    String s = "aaa" +
                        "b" + "c";
                    if(s instanceof
                        String);
                    boolean b = s.contains("a") ?
                        false :
                        true;
                    s +=
                        "b";
                    var a = Function::
                        identity;
                    int n =
                        1;
                    int n[] =
                        new int[0];
                    n =
                        2;
                }
                
                <T extends Serializable &
                        Comparable> T foo() {
                    return null;
                }
            }
        """)
    }
}
