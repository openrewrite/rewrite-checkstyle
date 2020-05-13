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

import org.openrewrite.java.JavaParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

open class StaticVariableNameTest : JavaParser() {
    @Test
    fun snakeName() {
        assertThat(_root_ide_package_.org.openrewrite.checkstyle.check.StaticVariableName.snakeCaseToCamel("CAMEL_CASE_NAME_1")).isEqualTo("camelCaseName1")
    }

    @Test
    fun dontChangeEveryField() {
        val a = parse("""
            import java.util.List;
            public class A {
               List MY_LIST;
            }
        """.trimIndent())

        val fixed = a.refactor().visit(_root_ide_package_.org.openrewrite.checkstyle.check.StaticVariableName.builder().build()).fix().fixed

        _root_ide_package_.org.openrewrite.checkstyle.check.assertRefactored(fixed, """
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

        val fixed = a.refactor().visit(_root_ide_package_.org.openrewrite.checkstyle.check.StaticVariableName.builder().build()).fix().fixed

        _root_ide_package_.org.openrewrite.checkstyle.check.assertRefactored(fixed, """
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

        val fixed = a.refactor().visit(_root_ide_package_.org.openrewrite.checkstyle.check.StaticVariableName.builder()
                .applyToPublic(false)
                .applyToPrivate(false)
                .applyToPackage(false)
                .build()
        ).fix().fixed

        _root_ide_package_.org.openrewrite.checkstyle.check.assertRefactored(fixed, """
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
