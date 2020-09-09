![Logo](https://github.com/openrewrite/rewrite/raw/master/doc/logo-oss.png)
### Eliminate Checkstyle issues. Automatically.

[![Build Status](https://circleci.com/gh/openrewrite/rewrite-checkstyle.svg?style=shield)](https://circleci.com/gh/openrewrite/rewrite-checkstyle)
[![Apache 2.0](https://img.shields.io/github/license/openrewrite/rewrite-checkstyle.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.openrewrite.plan/rewrite-checkstyle.svg)](https://mvnrepository.com/artifact/org.openrewrite.plan/rewrite-checkstyle)

### What is this?

This project implements a series of [Rewrite](https://github.com/openrewrite/rewrite) recipes and visitors that checks for and auto-remediates common Checkstyle issues. The check and remediation go together, so it does _not_ use Checkstyle for the checking, but rather performs an equivalent check according to the Checkstyle documentation. Each Rewrite Checkstyle rule provides the full set of options for the corresponding Checkstyle check.

This module parses your _existing_ Checkstyle configuration, supporting all the same configuration options that the Checkstyle check supports. It does its own checking, matching exactly the Checkstyle definition of each rule, and where it finds violations, fixes them automatically!

Since all of the rules check for syntactic and not semantic patterns, there is no need to ensure that the ASTs evaluated by Rewrite Checkstyle are fully type-attributed (i.e. there is no need to provide the compile classpath to `JavaParser`).

The list of currently supported checks is [here](https://github.com/openrewrite/rewrite-checkstyle/tree/master/src/main/java/org/openrewrite/checkstyle/check). Submit an issue to add support for additional checks. Even better, submit a PR!

## How to use?

See the full documentation at [docs.openrewrite.org](https://docs.openrewrite.org/java/checkstyle).
