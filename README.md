![Logo](https://github.com/openrewrite/rewrite/raw/master/doc/logo-oss.png)
### Eliminate Checkstyle issues. Automatically.

[![Build Status](https://circleci.com/gh/openrewrite/rewrite-checkstyle.svg?style=shield)](https://circleci.com/gh/openrewrite/rewrite-checkstyle)
[![Apache 2.0](https://img.shields.io/github/license/openrewrite/rewrite-checkstyle.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.openrewrite.plan/rewrite-checkstyle.svg)](https://mvnrepository.com/artifact/org.openrewrite.plan/rewrite-checkstyle)

### What is this?

This project implements a [Rewrite module](https://github.com/openrewrite/rewrite) that checks for and auto-remediates common Checkstyle issues. The check and remediation go together, so it does _not_ use Checkstyle for the checking, but rather performs an equivalent check according to the Checkstyle documentation. Each Rewrite Checkstyle rule provides the full set of options for the corresponding Checkstyle check.

### How do I use it?

This module parses your _existing_ Checkstyle configuration, supporting all the same configuration options that the Checkstyle check supports. It does its own checking, matching exactly the Checkstyle definition of each rule, and where it finds violations, fixes them automatically!

Since all of the rules check for syntactic and not semantic patterns, there is no need to ensure that the ASTs evaluated by Rewrite Checkstyle are fully type-attributed (i.e. there is no need to provide the compile classpath to `JavaParser`).

```java
// produce ASTs for all Java sources to check
List<J.CompilationUnit> cus = new JavaParser()
    .setLogCompilationWarningsAndErrors(false)
    .parse(sourceSetAsCollectionOfAbsolutePaths, optionalPathToRelativizeSources)

RewriteCheckstyle checkstyleRefactoring = new RewriteCheckstyle(inputStreamToCheckstyleXml);

for(J.CompilationUnit cu : cus) {
    Change<J.CompilationUnit> fixed = rewriteCheckstyle.apply(cu.refactor()).fix();

    if(!fixed.getAllRulesThatMadeChanges().isEmpty()) {
        // can overwrite the original source file with the fixes
        Files.writeString(new File(cu.getSourcePath()).toPath(), fixed.getFixed().print());
    }
}
```

In some cases, the Rewrite Checkstyle rule is a bit "smarter" than the original Checkstyle check, and so may make
changes where Checkstyle wouldn't report an issue at all (e.g. Rewrite's `SimplifyBooleanExpression` is aware of operator associativity and precedence where Checkstyle's check is not).

This project also contains a [`Main`](https://github.com/openrewrite/rewrite-checkstyle/blob/master/src/main/java/org/openrewrite/checkstyle/Main.java) class with a number of options that can be used to fix a project's source based on a checkstyle configuration file.
