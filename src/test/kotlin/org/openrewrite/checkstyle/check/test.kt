/**
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.checkstyle.check

import org.assertj.core.api.Assertions.assertThat
import org.openrewrite.java.JavaParser
import org.openrewrite.java.refactor.JavaRefactorVisitor
import org.openrewrite.java.tree.J

fun assertRefactored(cu: J.CompilationUnit, refactored: String) {
    assertThat(cu.printTrimmed()).isEqualTo(refactored.trimIndent())
}

fun JavaParser.assertUnchangedByRefactoring(visitor: JavaRefactorVisitor, original: String) {
    val cu = parse(original.trimIndent())
    val change = cu.refactor().visit(visitor).fix()
    assertRefactored(change.fixed, original.trimIndent())
    assertThat(change.rulesThatMadeChanges).isEmpty()
}