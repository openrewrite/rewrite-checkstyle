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
import org.openrewrite.java.JavaParser

open class EqualsAvoidsNullTest: JavaParser() {
    @Test
    fun invertConditional() {
        val a = parse("""
            public class A {
                {
                    String s = null;
                    if(s.equals("test")) {}
                    if(s.equalsIgnoreCase("test")) {}
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EqualsAvoidsNull(false)).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    String s = null;
                    if("test".equals(s)) {}
                    if("test".equalsIgnoreCase(s)) {}
                }
            }
        """)
    }

    @Test
    fun removeUnnecessaryNullCheckAndParens() {
        val a = parse("""
            public class A {
                {
                    String s = null;
                    if((s != null && s.equals("test"))) {}
                    if(s != null && s.equals("test")) {}
                    if(null != s && s.equals("test")) {}
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(EqualsAvoidsNull(false)).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    String s = null;
                    if("test".equals(s)) {}
                    if("test".equals(s)) {}
                    if("test".equals(s)) {}
                }
            }
        """)
    }
}
