package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class UnnecessaryParenthesesTest : JavaParser() {
    @Test
    fun simpleUnwrapping() {
        val a = parse("""
            import java.util.*;
            public class A {
                int square(int a, int b) {
                    int square = (a * b);

                    int sumOfSquares = 0;
                    for(int i = (0); i < 10; i++) {
                      sumOfSquares += (square(i * i, i));
                    }
                    double num = (10.0);

                    List<String> list = Arrays.asList("a1", "b1", "c1");
                    list.stream()
                      .filter((s) -> s.startsWith("c"))
                      .forEach(System.out::println);

                    return (square);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(UnnecessaryParentheses.builder()
                .build()).fix().fixed

        assertRefactored(fixed, """
            import java.util.*;
            public class A {
                int square(int a, int b) {
                    int square = a * b;

                    int sumOfSquares = 0;
                    for(int i = 0; i < 10; i++) {
                      sumOfSquares += square(i * i, i);
                    }
                    double num = 10.0;

                    List<String> list = Arrays.asList("a1", "b1", "c1");
                    list.stream()
                      .filter(s -> s.startsWith("c"))
                      .forEach(System.out::println);

                    return square;
                }
            }
        """)
    }

    @Test
    fun operatorPrecedence() {
        // NOTE: Don't think it's possible to change right-associativity with parentheses, so
        // we don't really need to consider associativity properties of operators

        val a = parse("""
            public class A {
                {
                    int x = 1;
                    int y = (a + b) * x;
                    int z = ((a + b) + c) * x;
                    int w = y + (x == y ? 1 : 2);
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(UnnecessaryParentheses.builder()
                .build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                {
                    int x = 1;
                    int y = (a + b) * x;
                    int z = (a + b + c) * x;
                    int w = y + (x == y ? 1 : 2);
                }
            }
        """)
    }
}
