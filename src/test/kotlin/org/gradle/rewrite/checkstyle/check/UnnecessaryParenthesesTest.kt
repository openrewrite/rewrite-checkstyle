package org.gradle.rewrite.checkstyle.check

import org.junit.jupiter.api.Test
import org.openrewrite.java.JavaParser

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
                .build()).fix(1).fixed

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
}
