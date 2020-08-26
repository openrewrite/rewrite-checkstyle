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
import org.openrewrite.checkstyle.policy.OperatorToken
import org.openrewrite.checkstyle.policy.WrapPolicy

open class OperatorWrapTest: CheckstyleRefactorVisitorTest(OperatorWrap()) {
    @Test
    fun operatorOnNewline() {
        setProperties("tokens" to OperatorToken.values().joinToString(","))
        assertRefactored(
                before = """
                    import java.io.*;
                    class A {
                        {
                            String s = "aaa" +
                                "b" + "c";
                            if(s instanceof
                                String);
                            boolean b = s.contains("a") ?
                                false :
                                true;
                            s +=
                                "b";
                            var a = Function::
                                identity;
                            int n =
                                1;
                            int n[] =
                                new int[0];
                            n =
                                2;
                        }
                        
                        <T extends Serializable &
                                Comparable> T foo() {
                            return null;
                        }
                    }
                """,
                after = """
                    import java.io.*;
                    class A {
                        {
                            String s = "aaa"
                                + "b" + "c";
                            if(s
                                instanceof String);
                            boolean b = s.contains("a")
                                ? false
                                : true;
                            s
                                += "b";
                            var a = Function
                                ::identity;
                            int n
                                = 1;
                            int n[]
                                = new int[0];
                            n
                                = 2;
                        }
                        
                        <T extends Serializable
                                & Comparable> T foo() {
                            return null;
                        }
                    }
                """
        )
    }

    @Test
    fun operatorOnEndOfLine() {
        setProperties(
                "option" to WrapPolicy.EOL,
                "tokens" to OperatorToken.values().joinToString(",")
        )
        assertRefactored(
                before = """
                    import java.io.*;
                    class A {
                        {
                            String s = "aaa"
                                + "b" + "c";
                            if(s
                                instanceof String);
                            boolean b = s.contains("a")
                                ? false
                                : true;
                            s
                                += "b";
                            var a = Function
                                ::identity;
                            int n
                                = 1;
                            int n[]
                                = new int[0];
                            n
                                = 2;
                        }
                        
                        <T extends Serializable
                                & Comparable> T foo() {
                            return null;
                        }
                    }
                """,
                after = """
                    import java.io.*;
                    class A {
                        {
                            String s = "aaa" +
                                "b" + "c";
                            if(s instanceof
                                String);
                            boolean b = s.contains("a") ?
                                false :
                                true;
                            s +=
                                "b";
                            var a = Function::
                                identity;
                            int n =
                                1;
                            int n[] =
                                new int[0];
                            n =
                                2;
                        }
                        
                        <T extends Serializable &
                                Comparable> T foo() {
                            return null;
                        }
                    }
                """
        )
    }
}
