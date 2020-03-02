package org.gradle.rewrite.checkstyle.check

import org.openrewrite.java.JavaParser
import org.junit.jupiter.api.Test

open class FallThroughTest : JavaParser() {
    @Test
    fun addBreaksFallthroughCases() {
        val a = parse("""
            public class A {
                int i;
                {
                    switch (i) {
                    case 0:
                        i++; // fall through

                    case 1:
                        i++;
                        // falls through
                    case 2:
                    case 3: {{
                    }}
                    case 4: {
                        i++;
                    }
                    // fallthrough
                    case 5:
                        i++;
                    /* fallthru */case 6:
                        i++;
                        // fall-through
                    case 7:
                        i++;
                        break;
                    case 8: {
                        // fallthrough
                    }
                    case 9:
                        i++;
                    }
                }
            }
        """.trimIndent())

        val fixed = a.refactor().visit(FallThrough.builder().build()).fix().fixed

        assertRefactored(fixed, """
            public class A {
                int i;
                {
                    switch (i) {
                    case 0:
                        i++; // fall through

                    case 1:
                        i++;
                        // falls through
                    case 2:
                        break;
                    case 3: {{
                        break;
                    }}
                    case 4: {
                        i++;
                    }
                    // fallthrough
                    case 5:
                        i++;
                    /* fallthru */case 6:
                        i++;
                        // fall-through
                    case 7:
                        i++;
                        break;
                    case 8: {
                        // fallthrough
                    }
                    case 9:
                        i++;
                    }
                }
            }
        """)
    }
}
