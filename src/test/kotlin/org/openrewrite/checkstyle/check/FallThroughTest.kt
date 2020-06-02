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

open class FallThroughTest : JavaParser() {
    private val defaultConfig = emptyModule("FallThrough")
    
    @Test
    fun addBreaksFallthroughCases() {
        val a = parse("""
            public class A {
                int i;
                {
                    switch (i) {
                    case 0:
                        i++; // fall through

                    case 1:
                        i++;
                        // falls through
                    case 2:
                    case 3: {{
                    }}
                    case 4: {
                        i++;
                    }
                    // fallthrough
                    case 5:
                        i++;
                    /* fallthru */case 6:
                        i++;
                        // fall-through
                    case 7:
                        i++;
                        break;
                    case 8: {
                        // fallthrough
                    }
                    case 9:
                        i++;
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(FallThrough.configure(defaultConfig)).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int i;
                {
                    switch (i) {
                    case 0:
                        i++; // fall through

                    case 1:
                        i++;
                        // falls through
                    case 2:
                        break;
                    case 3: {{
                        break;
                    }}
                    case 4: {
                        i++;
                    }
                    // fallthrough
                    case 5:
                        i++;
                    /* fallthru */case 6:
                        i++;
                        // fall-through
                    case 7:
                        i++;
                        break;
                    case 8: {
                        // fallthrough
                    }
                    case 9:
                        i++;
                    }
                }
            }
        """)
    }
}
