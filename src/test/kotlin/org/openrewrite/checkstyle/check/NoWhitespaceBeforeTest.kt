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
import org.openrewrite.config.MapConfigSource
import org.openrewrite.config.MapConfigSource.mapConfig
import org.openrewrite.java.JavaParser

open class NoWhitespaceBeforeTest : JavaParser() {
    private val defaultConfig = emptyModule("NoWhitespaceBefore")
    
    @Test
    fun noWhitespaceBefore() {
        val a = parse("""
            package a ;
            import java.util.* ;
            public abstract class A {
                int m ;
            
                {
                    int n = 0 ;
                    int n , o = 0 ;
                    n ++ ;
                    n -- ;
                    new A() 
                        .m = 2 ;
                    foo(1 , 2) .foo(3 , 4);
                    List <String > generic = new ArrayList < >() ;
                    var a = Function ::identity ;
                    for(int i = 0 , j = 0 ; i < 2 ; i++ , j++) ;
                    while(true) ;
                    do { } while(true) ;
                    for(String s : generic) ;
                }
                
                abstract A foo(int n , int m, int ... others) ;
            }
            
            interface B {
                void foo() ;
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NoWhitespaceBefore.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="NoWhitespaceBefore">
                                <property name="tokens" value="COMMA,SEMI,POST_INC,POST_DEC,DOT,GENERIC_START,GENERIC_END,ELLIPSIS,METHOD_REF"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            package a;
            import java.util.*;
            public abstract class A {
                int m;
            
                {
                    int n = 0;
                    int n, o = 0;
                    n++;
                    n--;
                    new A().m = 2;
                    foo(1, 2).foo(3, 4);
                    List<String> generic = new ArrayList<>();
                    var a = Function::identity;
                    for(int i = 0, j = 0; i < 2; i++, j++);
                    while(true);
                    do { } while(true);
                    for(String s : generic);
                }
                
                abstract A foo(int n, int m, int... others);
            }
            
            interface B {
                void foo();
            }
        """)
    }

    @Test
    fun allowLinebreaks() {
        val a = parse("""
            public class A {
                int m;
            
                {
                    new A()
                        .m = 2;
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(NoWhitespaceBefore.configure(mapConfig("checkstyle.config", """
                    <?xml version="1.0"?>
                    <!DOCTYPE module PUBLIC
                        "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
                        "https://checkstyle.org/dtds/configuration_1_3.dtd">
                    <module name="Checker">
                        <module name="TreeWalker">
                            <module name="NoWhitespaceBefore">
                                <property name="tokens" value="DOT"/>
                                <property name="allowLineBreaks" value="true"/>
                            </module>
                        </module>
                    </module>
                """.trimIndent()))).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int m;
            
                {
                    new A()
                        .m = 2;
                }
            }
        """)
    }

    @Test
    fun dontStripLastParameterSuffixInMethodDeclaration() {
        assertUnchangedByRefactoring(NoWhitespaceBefore.configure(defaultConfig), """
            package a;
            public abstract class A {
                abstract A foo(
                    int n,
                    int m
                );
            }
        """)
    }

    @Test
    fun dontStripLastArgumentSuffixInMethodInvocation() {
        assertUnchangedByRefactoring(NoWhitespaceBefore.configure(defaultConfig), """
            package a;
            public class A {
                {
                    int n = Math.min(
                        1,
                        2
                    );
                }
            }
        """)
    }

    @Test
    fun dontStripChainedMethodInvocationsByDefault() {
        assertUnchangedByRefactoring(NoWhitespaceBefore.configure(defaultConfig), """
            package a;
            public class A {
                public static A a(int... n) { return new A(); }
            
                {
                    A
                        .a()
                        .a(
                            1,
                            2
                        );
                }
            }
        """)
    }

    @Test
    fun dontStripStatementSuffixInTernaryConditionAndTrue() {
        assertUnchangedByRefactoring(NoWhitespaceBefore.configure(defaultConfig), """
            package a;
            import java.util.*;
            public class A {
                List l;
                {
                    int n = l.isEmpty() ? l.size() : 2;
                }
            }
        """)
    }

    @Test
    fun dontStripStatementSuffixPrecedingInstanceof() {
        assertUnchangedByRefactoring(NoWhitespaceBefore.configure(defaultConfig), """
            package a;
            import java.util.*;
            public class A {
                List l;
                {
                    boolean b = l.subList(0, 1) instanceof ArrayList;
                }
            }
        """)
    }

    @Test
    fun dontStripTryWithResourcesEndParens() {
        assertUnchangedByRefactoring(NoWhitespaceBefore.configure(defaultConfig), """
            import java.util.zip.*;
            import java.io.*;
            public class A {
                public static void main(String[] args) {
                    try (
                        InputStream source = new GZIPInputStream(new FileInputStream(args[0]));
                        OutputStream out = new FileOutputStream(args[1])
                    ) {
                        System.out.println("side effect");
                    } catch (Exception e) {
                    }
                }
            }
        """)
    }

    @Test
    fun dontStripAnnotationArguments() {
        assertUnchangedByRefactoring(NoWhitespaceBefore.configure(defaultConfig), """
            public class A {
                @SuppressFBWarnings(
                    value = "SECPR",
                    justification = "Usages of this method are not meant for cryptographic purposes"
                )
                void foo() {
                }
            }
        """)
    }
}
