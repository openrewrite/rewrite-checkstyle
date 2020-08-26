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

import org.junit.jupiter.api.BeforeEach
import org.openrewrite.RefactorVisitor
import org.openrewrite.RefactorVisitorTestForParser
import org.openrewrite.java.JavaParser
import org.openrewrite.java.tree.J

abstract class CheckstyleRefactorVisitorTest(private val visitor: CheckstyleRefactorVisitor) :
        RefactorVisitorTestForParser<J.CompilationUnit> {

    override val parser: JavaParser = JavaParser.fromJavaVersion().build()
    override val visitors: Iterable<RefactorVisitor<*>> = listOf(visitor)

    @BeforeEach
    fun clearConfig() {
        setProperties()
    }

    protected fun setProperties(vararg properties: Pair<String, Any>) {
        visitor.apply {
            setConfig(
                    """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="${visitor.javaClass.simpleName}">
                            ${
                                properties.joinToString("\n") {
                                    """<property name="${it.first}" value="${it.second}"/>"""
                                }
                            }
                            </module>
                        </module>
                    </module>
                """.trimIndent().trim()
            )
        }
    }
}
