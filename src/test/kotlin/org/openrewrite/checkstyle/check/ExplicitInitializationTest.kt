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

open class ExplicitInitializationTest : JavaParser() {
    @Test
    fun removeExplicitInitialization() {
        val a = parse("""
            class Test {
                private int a = 0;
                private long b = 0L;
                private short c = 0;
                private int d = 1;
                private long e = 2L;
                private int f;
                private char g = '\0';

                private boolean g = false;
                private boolean h = true;

                private Object i = new Object();
                private Object j = null;

                int k[] = null;
                int l[] = new int[0];
                
                private final Long l = null;
            }
        """.trimIndent())

        val fixed = a.refactor().visit(ExplicitInitialization()).fix().fixed

        assertRefactored(fixed, """
            class Test {
                private int a;
                private long b;
                private short c;
                private int d = 1;
                private long e = 2L;
                private int f;
                private char g;
            
                private boolean g;
                private boolean h = true;
            
                private Object i = new Object();
                private Object j;
            
                int k[];
                int l[] = new int[0];
                
                private final Long l = null;
            }
        """)
    }
}
