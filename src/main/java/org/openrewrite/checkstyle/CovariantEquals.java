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
package org.openrewrite.checkstyle;

import org.openrewrite.Cursor;
import org.openrewrite.AutoConfigure;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

@AutoConfigure
public class CovariantEquals extends CheckstyleRefactorVisitor {
    private final JavaParser javaParser;

    public CovariantEquals() {
        // TODO simplify this when conditional parser builder is added to rewrite-java
        JavaParser.Builder<? extends JavaParser, ?> javaParserBuilder;
        try {
            if (System.getProperty("java.version").startsWith("1.8")) {
                javaParserBuilder = (JavaParser.Builder<? extends JavaParser, ?>) Class
                        .forName("org.openrewrite.java.Java8Parser")
                        .getDeclaredMethod("builder")
                        .invoke(null);
            } else {
                javaParserBuilder = (JavaParser.Builder<? extends JavaParser, ?>) Class
                        .forName("org.openrewrite.java.Java11Parser")
                        .getDeclaredMethod("builder")
                        .invoke(null);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create a Java parser instance. " +
                    "`rewrite-java-8` or `rewrite-java-11` must be on the classpath.");
        }

        this.javaParser = javaParserBuilder.build();
        setCursoringOn();
    }

    @Override
    public J visitMethod(J.MethodDecl method) {
        J.MethodDecl m = refactor(method, super::visitMethod);
        JavaType.Class classType = TypeUtils.asClass(enclosingClass().getType());

        if (method.getSimpleName().equals("equals") &&
                method.hasModifier("public") &&
                method.getReturnTypeExpr() != null &&
                JavaType.Primitive.Boolean.equals(method.getReturnTypeExpr().getType()) &&
                method.getParams().getParams().size() == 1 &&
                method.getParams().getParams().stream().allMatch(p -> p.hasClassType(classType))) {

            String methodPrefix = methodFormattingPrefix(method);
            String methodIndent = formatter.findIndent(enclosingBlock().getIndent(), method).getPrefix();

            m = maybeAddOverrideAnnotation(m, methodPrefix, methodIndent);

            if (!m.getModifiers().isEmpty()) {
                m = m.withModifiers(formatFirstPrefix(m.getModifiers(), methodIndent));
            }

            J.VariableDecls.NamedVar oldParamName = ((J.VariableDecls) method.getParams().getParams().iterator().next())
                    .getVars().iterator().next();
            J.VariableDecls.NamedVar paramName = oldParamName
                    .withName(oldParamName.getName().withName("o".equals(oldParamName.getSimpleName()) ? "other" : "o"));

            m = changeParameterNameAndType(m, paramName);
            m = addEqualsBody(m, oldParamName, paramName);
        }

        return m;
    }

    private J.MethodDecl addEqualsBody(J.MethodDecl method, J.VariableDecls.NamedVar oldParamName, J.VariableDecls.NamedVar paramName) {
        String paramNameStr = paramName.printTrimmed();
        List<Statement> equalsBody = TreeBuilder.buildSnippet(javaParser, enclosingCompilationUnit(), new Cursor(getCursor(), method.getBody()),
                "if (this == " + paramNameStr + ") return true;\n" +
                        "if (" + paramNameStr + " == null || getClass() != " + paramNameStr + ".getClass()) return false;\n" +
                        "Test " + oldParamName.printTrimmed() + " = (Test) " + paramNameStr + ";\n");

        equalsBody.addAll(method.getBody().getStatements());

        return method.withBody(method.getBody().withStatements(equalsBody));
    }

    /**
     * Change the parameter type to Object and the name to other 'other' or 'o'.
     */
    private J.MethodDecl changeParameterNameAndType(J.MethodDecl method, J.VariableDecls.NamedVar paramName) {
        return method.withParams(method.getParams().withParams(method.getParams().getParams().stream()
                .map(p -> ((J.VariableDecls) p)
                        .withVars(singletonList(paramName))
                        .withTypeExpr(TreeBuilder.buildName("Object")))
                .collect(toList())));
    }

    private J.MethodDecl maybeAddOverrideAnnotation(J.MethodDecl method, String methodPrefix, String methodIndent) {
        if (method.getAnnotations().stream().noneMatch(ann -> TypeUtils.isOfClassType(ann.getType(), "java.lang.Override"))) {
            List<J.Annotation> annotations = new ArrayList<>(method.getAnnotations());
            annotations.add(
                    new J.Annotation(
                            randomId(),
                            J.Ident.build(randomId(), "Override", JavaType.Class.build("java.lang.Override"), EMPTY),
                            null,
                            method.getAnnotations().isEmpty() ? format(methodPrefix) : format(methodIndent)
                    )
            );

            return method.withAnnotations(annotations);
        }
        return method;
    }

    private String methodFormattingPrefix(J.MethodDecl method) {
        if (!method.getAnnotations().isEmpty()) {
            return method.getAnnotations().iterator().next().getFormatting().getPrefix();
        }

        // we know this method is public, so there must be a modifier;
        return method.getModifiers().iterator().next().getFormatting().getPrefix();
    }
}
