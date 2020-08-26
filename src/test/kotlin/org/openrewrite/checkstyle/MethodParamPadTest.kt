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
import org.openrewrite.checkstyle.policy.PadPolicy

open class MethodParamPadTest: CheckstyleRefactorVisitorTest(MethodParamPad()) {
    @Test
    fun noSpaceInitializerPadding() = assertRefactored(
            before = """
                public class A extends B {
                    A () {
                        super ();
                    }
                
                    void foo (int n) {
                        A a = new A ();
                        foo (0);
                    }
                }
                
                class B {}
                
                enum E {
                    E1 ()
                }
            """,
            after = """
                public class A extends B {
                    A() {
                        super();
                    }
                
                    void foo(int n) {
                        A a = new A();
                        foo(0);
                    }
                }
                
                class B {}
                
                enum E {
                    E1()
                }
            """
    )

    @Test
    fun spaceInitializerPadding() {
        setProperties("option" to PadPolicy.SPACE)
        assertRefactored(
                before = """
                    public class A extends B {
                        A() {
                            super();
                        }
                    
                        void foo(int n) {
                            A a = new A();
                            foo(0);
                        }
                    }
                    
                    class B {}
                    
                    enum E {
                        E1()
                    }
                """,
                after = """
                    public class A extends B {
                        A () {
                            super ();
                        }
                    
                        void foo (int n) {
                            A a = new A ();
                            foo (0);
                        }
                    }
                    
                    class B {}
                    
                    enum E {
                        E1 ()
                    }
                """
        )
    }

    @Test
    fun allowLineBreaks() {
        setProperties("allowLineBreaks" to true)
        assertUnchanged(
                before = """
                    public class A extends B {
                        void foo
                            (int n) {}
                    }
                """
        )
    }
}
