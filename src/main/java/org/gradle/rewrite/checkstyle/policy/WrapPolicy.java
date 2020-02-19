package org.gradle.rewrite.checkstyle.policy;

public enum WrapPolicy {
    /**
     * The token must be on a new line. For example:
     * <pre>
     *     someVariable = aBigVariableNameToMakeThings + "this may work"
     *                    + lookVeryInteresting;
     * </pre>
     */
    NL,

    /**
     * The token must be at the end of the line. For example:
     * <pre>
     *     someVariable = aBigVariableNameToMakeThings + "this may work" +
     *                    lookVeryInteresting;
     * </pre>
     */
    EOL,
}
