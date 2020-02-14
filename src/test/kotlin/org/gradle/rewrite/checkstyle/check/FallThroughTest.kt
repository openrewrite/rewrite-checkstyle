package org.gradle.rewrite.checkstyle.check

import com.netflix.rewrite.parse.OpenJdkParser
import com.netflix.rewrite.parse.Parser
import org.junit.jupiter.api.Test

open class FallThroughTest : Parser by OpenJdkParser() {
    @Test
    fun addBreaksFallthroughCases() {
        val a = parse("""
            public class A {
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

        val fixed = a.refactor().run(FallThrough.builder().build()).fix()

        assertRefactored(fixed, """
            public class A {
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
