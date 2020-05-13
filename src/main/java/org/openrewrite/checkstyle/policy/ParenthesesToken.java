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

public enum ParenthesesToken {
    EXPR,
    IDENT,
    NUM_DOUBLE,
    NUM_FLOAT,
    NUM_INT,
    NUM_LONG,
    STRING_LITERAL,
    LITERAL_NULL,
    LITERAL_FALSE,
    LITERAL_TRUE,
    ASSIGN,
    BAND_ASSIGN,
    BOR_ASSIGN,
    BSR_ASSIGN,
    BXOR_ASSIGN,
    DIV_ASSIGN,
    MINUS_ASSIGN,
    MOD_ASSIGN,
    PLUS_ASSIGN,
    SL_ASSIGN,
    SR_ASSIGN,
    STAR_ASSIGN,
    LAMBDA
}
