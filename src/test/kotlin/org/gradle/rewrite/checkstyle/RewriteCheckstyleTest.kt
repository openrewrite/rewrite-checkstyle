package org.gradle.rewrite.checkstyle

import org.assertj.core.api.Assertions.assertThat
import org.gradle.rewrite.checkstyle.check.CovariantEquals
import org.gradle.rewrite.checkstyle.check.DefaultComesLast
import org.gradle.rewrite.checkstyle.check.SimplifyBooleanExpression
import org.gradle.rewrite.checkstyle.check.SimplifyBooleanReturn
import org.junit.jupiter.api.Test

class RewriteCheckstyleTest {
    @Test
    fun deserializeConfig() {
        val checkstyleConfig = """
            <?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                    "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <module name="TreeWalker">
                    <!-- Regexp -->
                    <module name="Regexp">
                        <property name="id" value="sysout"/>
                        <property name="format" value="System\.out\.println"/>
                    </module>
                    
                    <module name="UnusedImports">
                        <property name="processJavadoc" value="true" />
                    </module>
                    <module name="CovariantEquals"/>
                    <module name="DefaultComesLast"/>
                    <module name="SimplifyBooleanExpression"/>
                    <module name="SimplifyBooleanReturn"/>
                    <module name="EmptyBlock"/>
                </module>
            </module>
        """.trimIndent()

        val visitors = RewriteCheckstyle.fromConfiguration(checkstyleConfig.byteInputStream())

        assertThat(visitors)
                .hasAtLeastOneElementOfType(CovariantEquals::class.java)
                .hasAtLeastOneElementOfType(DefaultComesLast::class.java)
                .hasAtLeastOneElementOfType(SimplifyBooleanExpression::class.java)
                .hasAtLeastOneElementOfType(SimplifyBooleanReturn::class.java)
    }
}
