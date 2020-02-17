package org.gradle.rewrite.checkstyle.policy;

public enum LeftCurlyPolicy {
    /**
     * The brace must always be on the end of the line. For example:#
     * if (condition) {
     * ...
     */
    EOL,

    /**
     * The brace must always be on a new line. For example:
     * <pre>{@code
     * if (condition)
     * {
     * ...
     * }</pre>
     */
    NL,

    /**
     * If the statement/expression/declaration connected to the brace spans multiple lines, then apply nl rule.
     * Otherwise apply the eol rule. nlow is a mnemonic for "new line on wrap". For the example above:
     * if (condition) {
     * ...
     * <p>
     * But for a statement spanning multiple lines:
     * if (condition1 && condition2 &&
     * condition3 && condition4)
     * {
     * ...
     */
    NLOW
}
