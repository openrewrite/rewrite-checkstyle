package org.gradle.rewrite.checkstyle.policy;

public enum PadPolicy {
    /**
     * Do not pad. For example, method(a, b);
     */
    NOSPACE,

    /**
     * Ensure padding. For example, method( a, b );
     */
    SPACE
}
