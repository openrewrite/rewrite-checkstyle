package org.gradle.rewrite.checkstyle.check;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Just ensuring that the understanding of associativity built into this library is correct.
 */
class UnnecessaryParenthesesAssociativityTest {
    @Test
    void associativity() {
        int i = 100000;
        assertThat(2 ^ (i >>> 8))
                .isEqualTo(2 ^ i >>> 8)
                .isNotEqualTo((2 ^ i) >>> 8);
    }
}
