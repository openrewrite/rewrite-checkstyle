package org.openrewrite.checkstyle

import org.openrewrite.java.JavaParser
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

abstract class CheckstyleRefactorVisitorTest(private val visitor: KClass<out CheckstyleRefactorVisitor>) : JavaParser() {
    fun configXml(vararg properties: Pair<String, Any>) = visitor.createInstance().apply {
        setConfig(
        """
            <?xml version="1.0"?>
            <!DOCTYPE module PUBLIC
                "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                "https://checkstyle.org/dtds/configuration_1_3.dtd">
            <module name="Checker">
                <module name="TreeWalker">
                    <module name="${visitor.simpleName}">
                    ${properties.joinToString("\n") {
                    """<property name="${it.first}" value="${it.second}"/>"""
                }}
                    </module>
                </module>
            </module>
        """.trimIndent().trim()
        )
    }
}
