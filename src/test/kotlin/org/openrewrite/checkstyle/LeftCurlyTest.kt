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

import org.junit.jupiter.api.Test
import org.openrewrite.checkstyle.policy.LeftCurlyPolicy

open class LeftCurlyTest: CheckstyleRefactorVisitorTest(LeftCurly::class) {
    @Test
    fun eol() {
        val a = jp.parse("""
            class A
            {
                {
                    if(1 == 2)
                    {
                    }
                }
            
                private static final int N = 1;
                static
                {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(configXml()).fix().fixed

        assertRefactored(fixed, """
            class A {
                {
                    if(1 == 2) {
                    }
                }
            
                private static final int N = 1;
                static {
                }
            }
        """)
    }

    @Test
    fun nl() {
        val a = jp.parse("""
            class A {
                {
                    if(1 == 2) {
                    }
                }
            
                private static final int N = 1;
                static {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(configXml("option" to LeftCurlyPolicy.NL))
                .fix().fixed

        assertRefactored(fixed, """
            class A
            {
                {
                    if(1 == 2)
                    {
                    }
                }
            
                private static final int N = 1;
                static
                {
                }
            }
        """)
    }

    @Test
    fun nlow() {
        val a = jp.parse("""
            class A {
                {
                    if(1 == 2)
                    {
                    }
                    if(1 == 2 &&
                        3 == 4) {
                    }
                }
            
                private static final int N = 1;
                static {
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(configXml("option" to LeftCurlyPolicy.NLOW))
                .fix().fixed

        assertRefactored(fixed, """
            class A {
                {
                    if(1 == 2) {
                    }
                    if(1 == 2 &&
                        3 == 4)
                    {
                    }
                }
            
                private static final int N = 1;
                static
                {
                }
            }
        """)
    }

    @Test
    fun caseBlocks() {
        val a = jp.parse("""
            class A {
                {
                    switch(1) {
                    case 1:
                    {
                    }
                    case 2: {
                    }
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(configXml("option" to LeftCurlyPolicy.EOL))
                .fix().fixed

        assertRefactored(fixed, """
            class A {
                {
                    switch(1) {
                    case 1: {
                    }
                    case 2: {
                    }
                    }
                }
            }
        """)
    }

    @Test
    fun dontStripNewClassInstanceInitializers() {
        jp.assertUnchangedByRefactoring(configXml(), """
            public class JacksonUtils {
                static ObjectMapper stdConfigure(ObjectMapper mapper) {
                    return mapper
                        .registerModule(new SimpleModule() {
                            {
                                addSerializer(new InstantSerializer());
                            }
                        });
                }
            }
        """)
    }
}
