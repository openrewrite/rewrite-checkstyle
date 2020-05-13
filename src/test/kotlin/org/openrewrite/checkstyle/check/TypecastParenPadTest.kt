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
import org.openrewrite.checkstyle.policy.PadPolicy
import org.junit.jupiter.api.Test

open class TypecastParenPadTest : JavaParser() {
    private val padded = """
        public class A {
            { 
                long m = 0L;
                int n = ( int ) m;
            }
        }
    """.trimIndent()

    private val unpadded = """
        public class A {
            { 
                long m = 0L;
                int n = (int) m;
            }
        }
    """.trimIndent()

    @Test
    fun shouldPadTypecast() {
        val fixed = parse(unpadded).refactor().visit(_root_ide_package_.org.openrewrite.checkstyle.check.TypecastParenPad(PadPolicy.SPACE)).fix().fixed
        _root_ide_package_.org.openrewrite.checkstyle.check.assertRefactored(fixed, padded)
    }

    @Test
    fun shouldUnpadTypecast() {
        val fixed = parse(padded).refactor().visit(_root_ide_package_.org.openrewrite.checkstyle.check.TypecastParenPad()).fix().fixed
        _root_ide_package_.org.openrewrite.checkstyle.check.assertRefactored(fixed, unpadded)
    }
}
