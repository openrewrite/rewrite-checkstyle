package org.gradle.rewrite.checkstyle.check

import org.openrewrite.Parser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

open class StaticVariableNameTest : Parser() {
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

        val fixed = a.refactor().visit(StaticVariableName.builder().build()).fix().fixed

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

        val fixed = a.refactor().visit(StaticVariableName.builder().build()).fix().fixed

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

        val fixed = a.refactor().visit(StaticVariableName.builder()
                .applyToPublic(false)
                .applyToPrivate(false)
                .applyToPackage(false)
                .build()
        ).fix().fixed

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
