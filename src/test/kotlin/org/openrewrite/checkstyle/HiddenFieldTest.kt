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

open class HiddenFieldTest : CheckstyleRefactorVisitorTest(HiddenField()) {
    @Test
    fun renameHiddenFields() = assertRefactored(
            dependencies = listOf("""
                public class B {
                    protected int n2;
                    int n3;
                    private int n4;
                }
            """),
            before = """
                public class A extends B {
                    int n;
                    int n1;
    
                    class C {
                        public void foo(int n) {
                            int n1 = 2;
                        }
                    }
                    
                    static class D {
                        public void foo(int n) {
                        }
                    }
                }
            """,
            after = """
                public class A extends B {
                    int n;
                    int n1;
    
                    class C {
                        public void foo(int n4) {
                            int n5 = 2;
                        }
                    }
                    
                    static class D {
                        public void foo(int n) {
                        }
                    }
                }
            """
    )

    @Test
    fun ignorePattern() {
        setProperties("ignoreFormat" to "\\w+")
        assertUnchanged(
                before = """
                    public class A {
                        int n;
                        
                        public void foo(int n) {
                        }
                    }
                """
        )
    }

    @Test
    fun ignoreConstructorParameter() {
        setProperties("ignoreConstructorParameter" to true)
        assertUnchanged(
                before = """
                    public class A {
                        int n;
                        
                        A(int n) {
                        }
                    }
                """
        )
    }

    @Test
    fun ignoreSetter() {
        setProperties("ignoreSetter" to true)
        assertRefactored(
                before = """
                    public class A {
                        int n;
                        
                        public void setN(int n) {
                        }
                        
                        public A setN(int n) {
                            return this;
                        }
                    }
                """,
                after = """
                    public class A {
                        int n;
                        
                        public void setN(int n) {
                        }
                        
                        public A setN(int n1) {
                            return this;
                        }
                    }
                """
        )
    }

    @Test
    fun ignoreSetterThatReturnsItsClass() {
        setProperties(
                "ignoreSetter" to true,
                "setterCanReturnItsClass" to true
        )
        assertUnchanged(
                before = """
                    public class A {
                        int n;
                        
                        public A setN(int n) {
                            return this;
                        }
                    }
                """
        )
    }

    @Test
    fun ignoreAbstractMethods() {
        setProperties("ignoreAbstractMethods" to true)
        assertUnchanged(
                before = """
                    public abstract class A {
                        int n;
                        
                        public abstract void foo(int n);
                    }
                """
        )
    }
}
