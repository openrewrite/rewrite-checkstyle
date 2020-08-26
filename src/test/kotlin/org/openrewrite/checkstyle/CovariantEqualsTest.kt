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

open class CovariantEqualsTest : CheckstyleRefactorVisitorTest(CovariantEquals()) {
    @Test
    fun replaceWithNonCovariantEquals() = assertRefactored(
            before = """
                class Test {
                    int n;
                    
                    public boolean equals(Test t) {
                        return n == t.n;
                    }
                }
            """,
            after = """
                class Test {
                    int n;
    
                    @Override
                    public boolean equals(Object o) {
                        if (this == o) return true;
                        if (o == null || getClass() != o.getClass()) return false;
                        Test t = (Test) o;
                        return n == t.n;
                    }
                }
            """
    )
}
