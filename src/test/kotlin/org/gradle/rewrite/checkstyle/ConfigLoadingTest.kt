package org.gradle.rewrite.checkstyle

import com.puppycrawl.tools.checkstyle.ConfigurationLoader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.xml.sax.InputSource

class ConfigLoadingTest {
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
                </module>
            </module>
        """.trimIndent()

        val config = ConfigurationLoader.loadConfiguration(InputSource(checkstyleConfig.reader()), System::getProperty,
                ConfigurationLoader.IgnoredModulesOptions.OMIT)

        assertThat(config.children[0].children).hasSize(2)
    }
}
