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

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class SimplifyBooleanExpressionTest : JavaParser() {
    @Test
    fun simplifyBooleanExpression() {
        val a = parse("""
            public class A {
                {
                    boolean a = !false;
                    boolean b = (a == true);
                    boolean c = b || true;
                    boolean d = c || c;
                    boolean e = d && d;
                    boolean f = (e == true) || e;
                    boolean g = f && false;
                    boolean h = !!g;
                    boolean i = (a != false);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(SimplifyBooleanExpression()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    boolean a = true;
                    boolean b = a;
                    boolean c = true;
                    boolean d = c;
                    boolean e = d;
                    boolean f = e;
                    boolean g = false;
                    boolean h = g;
                    boolean i = a;
                }
            }
        """)
    }
}
