/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.checkstyle.policy;

public enum LeftCurlyPolicy {
    /**
     * The brace must always be on the end of the line. For example:#
     * <pre>{@code
     * if (condition) {
     * ...
     * }
     * }</pre>
     */
    EOL,

    /**
     * The brace must always be on a new line. For example:
     * <pre>{@code
     * if (condition)
     * {
     * ...
     * }
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
     * <pre>{@code
     * if (condition1 && condition2 &&
     * condition3 && condition4)
     * {
     * ...
     * }
     * }</pre>
     */
    NLOW
}
