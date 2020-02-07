package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

open class StaticVariableNameTest : Parser by OpenJdkParser() {
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

        val fixed = a.refactor().run(StaticVariableName.builder().build()).fix()

        assertRefactored(fixed, """
            import java.util.List;
            public class A {
               List MY_LIST;
            }
        """)
    }

    @Test
    fun changeSingleVariableField() {
        val a = parse("""
            import java.util.List;
            public class A {
               static List MY_LIST;
            }
        """.trimIndent())

        val fixed = a.refactor().run(StaticVariableName.builder().build()).fix()

        assertRefactored(fixed, """
            import java.util.List;
            public class A {
               static List myList;
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

        val fixed = a.refactor().run(StaticVariableName.builder()
                .applyToPublic(false)
                .applyToPrivate(false)
                .applyToPackage(false)
                .build()
        ).fix()

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
