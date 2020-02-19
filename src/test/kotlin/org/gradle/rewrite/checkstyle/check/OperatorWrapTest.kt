package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.gradle.rewrite.checkstyle.policy.OperatorToken
import org.gradle.rewrite.checkstyle.policy.WrapPolicy
import org.junit.jupiter.api.Test

open class OperatorWrapTest : Parser by OpenJdkParser() {
    @Test
    fun operatorOnNewline() {
        val a = parse("""
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
        """.trimIndent())

        val fixed = a.refactor().visit(OperatorWrap.builder()
                .tokens(OperatorToken.values().toSet())
                .build()).fix().fixed

        assertRefactored(fixed, """
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
        """)
    }

    @Test
    fun operatorOnEndOfLine() {
        val a = parse("""
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
        """.trimIndent())

        val fixed = a.refactor().visit(OperatorWrap.builder()
                .option(WrapPolicy.EOL)
                .tokens(OperatorToken.values().toSet())
                .build()).fix().fixed

        assertRefactored(fixed, """
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
        """)
    }
}
