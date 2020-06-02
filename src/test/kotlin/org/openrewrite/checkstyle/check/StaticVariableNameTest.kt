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

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.openrewrite.config.MapConfigSource
import org.openrewrite.config.MapConfigSource.mapConfig
import org.openrewrite.java.JavaParser

open class StaticVariableNameTest : JavaParser() {
    private val defaultConfig = emptyModule("StaticVariableName")

    @Test
    fun snakeName() {
        assertThat(StaticVariableName.snakeCaseToCamel("CAMEL_CASE_NAME_1")).isEqualTo("camelCaseName1")
    }

    @Test
    fun dontChangeEveryField() {
        val a = parse("""
            import java.util.List;
            public class A {
               List MY_LIST;
            }
        """.trimIndent())

        val fixed = a.refactor().visit(StaticVariableName.configure(defaultConfig)).fix().fixed

        assertRefactored(fixed, """
            import java.util.List;
            public class A {
               List MY_LIST;
            }
        """)
    }

    @Test
    fun changeSingleVariableFieldAndReference() {
        val a = parse("""
            import java.util.*;
            public class A {
               static List<String> MY_LIST;
               
               static {
                   MY_LIST = new ArrayList<>();
               }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(StaticVariableName.configure(defaultConfig)).fix().fixed

        assertRefactored(fixed, """
            import java.util.*;
            public class A {
               static List<String> myList;
               
               static {
                   myList = new ArrayList<>();
               }
            }
        """)
    }

    @Test
    fun changeOnlyMatchingVisibility() {
        val a = parse("""
            import java.util.List;
            public class A {
               static List MY_LIST;
               private static List MY_PRIVATE_LIST;
               public static List MY_PUBLIC_LIST;
               protected static List MY_PROTECTED_LIST;
            }
        """.trimIndent())

        val fixed = a.refactor().visit(StaticVariableName.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="StaticVariableName">
                                <property name="applyToPublic" value="false"/>
                                <property name="applyToPackage" value="false"/>
                                <property name="applyToPrivate" value="false"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            import java.util.List;
            public class A {
               static List MY_LIST;
               private static List MY_PRIVATE_LIST;
               public static List MY_PUBLIC_LIST;
               protected static List myProtectedList;
            }
        """)
    }
}
