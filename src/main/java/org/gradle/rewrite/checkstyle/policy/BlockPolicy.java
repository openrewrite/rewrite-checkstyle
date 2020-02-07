package org.gradle.rewrite.checkstyle.policy;

/**
 * Defined in <a href="https://checkstyle.sourceforge.io/property_types.html#block">Block</a>.
 */
public enum BlockPolicy {
    /**
     * Require that there is some text in the block. For example:
     *     catch (Exception ex) {
     *         // This is a bad coding practice
     *     }
     */
    Text,

    /**
     * Require that there is a statement in the block. For example:
     *     finally {
     *         lock.release();
     *     }
     */
    Statement
}
