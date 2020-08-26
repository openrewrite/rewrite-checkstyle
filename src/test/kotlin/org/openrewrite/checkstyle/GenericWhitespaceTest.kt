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

class GenericWhitespaceTest : CheckstyleRefactorVisitorTest(GenericWhitespace()) {
    @Test
    fun genericWhitespace() = assertRefactored(
            before = """
                import java.util.*;
                public class A < T1, T2 > {
                    Map < String, Integer > map;
                    
                    { 
                        boolean same = this.< Integer, Integer >foo(1, 2);
                        map = new HashMap <>();
                        
                        List < String > list = ImmutableList.Builder< String >::new;
                        Collections.sort(list, Comparable::< String >compareTo);
                    }
                    
                    < K, V extends Number > boolean foo(K k, V v) {
                        return true;    
                    }
                }
            """,
            after = """
                import java.util.*;
                public class A<T1, T2> {
                    Map<String, Integer> map;
                    
                    { 
                        boolean same = this.<Integer, Integer>foo(1, 2);
                        map = new HashMap<>();
                        
                        List<String> list = ImmutableList.Builder<String>::new;
                        Collections.sort(list, Comparable::<String>compareTo);
                    }
                    
                    <K, V extends Number> boolean foo(K k, V v) {
                        return true;    
                    }
                }
            """
    )

    @Test
    fun stripUpToLinebreak() = assertRefactored(
            before = """
                import java.util.HashMap;
                
                // extra space after 'HashMap<' and after 'Integer'
                public class A extends HashMap< 
                        String,
                        Integer 
                    > {
                }
            """,
            after = """
                import java.util.HashMap;
                
                // extra space after 'HashMap<' and after 'Integer'
                public class A extends HashMap<
                        String,
                        Integer
                    > {
                }
            """
    )

    @Test
    fun doesntConsiderLinebreaksWhitespace() = assertUnchanged(
            before = """
                import java.util.HashMap;
                
                public class A extends HashMap<
                        String,
                        String
                    > {
                }
            """
    )
}
