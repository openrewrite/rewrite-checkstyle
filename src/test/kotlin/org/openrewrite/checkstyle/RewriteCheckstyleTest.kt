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
package org.openrewrite.checkstyle

import org.assertj.core.api.Assertions.assertThat
import org.openrewrite.checkstyle.check.DefaultComesLast
import org.openrewrite.checkstyle.check.SimplifyBooleanExpression
import org.openrewrite.checkstyle.check.SimplifyBooleanReturn
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.openrewrite.java.tree.J
import java.nio.file.Files
import java.nio.file.Path

class RewriteCheckstyleTest {
    @Test
    fun deserializeConfig(@TempDir tempDir: Path) {
        Files.writeString(tempDir.resolve("suppressions.xml"), """
            <?xml version="1.0"?>

            <!DOCTYPE suppressions PUBLIC
              "-//Puppy Crawl//DTD Suppressions 1.1//EN"
              "http://www.puppycrawl.com/dtds/suppressions_1_1.dtd">

            <suppressions>
              <suppress checks="." files="src[\/]generated"/>
            </suppressions>

        """.trimIndent())

        val checkstyleConfig = """
            <?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                    "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                    "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <module name="SuppressionFilter">
                    <property name="file" value="${'$'}{config_loc}/suppressions.xml"/>
                </module>
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

        val main = J.CompilationUnit.buildEmptyClass(Path.of("src", "main"), "", "Main")
        val generated = J.CompilationUnit.buildEmptyClass(Path.of("src", "generated"), "", "Generated")

        val rewriteCheckstyle = RewriteCheckstyle(checkstyleConfig.byteInputStream(),
                setOf("CovariantEquals"),
                mapOf("config_loc" to tempDir.toFile().toString()))

        assertThat(rewriteCheckstyle.apply(main.refactor()).visitors)
                .hasAtLeastOneElementOfType(DefaultComesLast::class.java)
                .hasAtLeastOneElementOfType(SimplifyBooleanExpression::class.java)
                .hasAtLeastOneElementOfType(SimplifyBooleanReturn::class.java)

        assertThat(rewriteCheckstyle.apply(generated.refactor()).visitors).isEmpty()
    }
}
