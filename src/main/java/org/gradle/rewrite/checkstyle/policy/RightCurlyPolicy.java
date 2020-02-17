package org.gradle.rewrite.checkstyle.policy;

public enum RightCurlyPolicy {
    /**
     * The brace must be alone on the line. For example:
     * <pre>{@code
     *     try {
     *         ...
     *     }
     *     finally {
     *         ...
     *     }
     * }</code>
     */
    ALONE,

    /**
     * The brace must be alone on the line, yet single-line format of block is allowed. For example:
     * <pre>{@code
     *     // Brace is alone on the line
     *     try {
     *         ...
     *     }
     *     finally {
     *         ...
     *     }
     *
     *     // Single-line format of block
     *     public long getId() { return id; }
     * }</pre>
     */
    ALONE_OR_SINGLELINE,

    /**
     * Works like alone_or_singleline but the brace should be on the same line as the next part of a multi-block statement (one that directly contains multiple blocks: if/else-if/else or try/catch/finally). If no next part of a multi-block statement present, brace must be alone on line. It also allows single-line format of multi-block statements.
     * Examples:
     * <pre>{@code
     *     public long getId() {return id;} // this is OK, it is single line
     *
     *     // try-catch-finally blocks
     *     try {
     *         ...
     *     } catch (Exception ex) { // this is OK
     *         ...
     *     } finally { // this is OK
     *         ...
     *     }
     *
     *     try {
     *         ...
     *     } // this is NOT OK, not on the same line as the next part of a multi-block statement
     *     catch (Exception ex) {
     *           ...
     *     } // this is NOT OK, not on the same line as the next part of a multi-block statement
     *     finally {
     *           ...
     *     }
     *
     *     // if-else blocks
     *     if (a > 0) {
     *        ...
     *     } else { // this is OK
     *        ...
     *     }
     *
     *     if (a > 0) {
     *        ...
     *     } // this is NOT OK, not on the same line as the next part of a multi-block statement
     *     else {
     *        ...
     *     }
     *
     *     if (a > 0) {
     *        ...
     *     } int i = 5; // NOT OK, no next part of a multi-block statement, so should be alone
     *
     *     Thread t = new Thread(new Runnable() {
     *        {@literal @}Override
     *        public void run() {
     *                   ...
     *        } // this is OK, should be alone as next part of a multi-block statement is absent
     *     }); // this case is out of scope of RightCurly Check (see issue #5945)
     *
     *     if (a > 0) { ... } // OK, single-line multi-block statement
     *     if (a > 0) { ... } else { ... } // OK, single-line multi-block statement
     *     if (a > 0) {
     *         ...
     *     } else { ... } // OK, single-line multi-block statement
     * }</pre>
     */
    SAME
}
