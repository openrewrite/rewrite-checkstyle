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
package org.openrewrite.checkstyle.check;

import org.eclipse.microprofile.config.Config;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.checkstyle.policy.Token;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaSourceVisitor;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.java.tree.TypeUtils.getVisibleSupertypeMembers;

public class HiddenField extends CheckstyleRefactorVisitor {
    private static final Pattern NAME_PATTERN = Pattern.compile("(.+)(\\d+)");
    private static final Set<Token> DEFAULT_TOKENS = Set.of(Token.VARIABLE_DEF, Token.PARAMETER_DEF, Token.LAMBDA);

    @Nullable
    private final Pattern ignoreFormat;

    private final boolean ignoreConstructorParameter;
    private final boolean ignoreSetter;
    private final boolean setterCanReturnItsClass;
    private final boolean ignoreAbstractMethods;
    private final Set<Token> tokens;

    public HiddenField(@Nullable Pattern ignoreFormat, boolean ignoreConstructorParameter, boolean ignoreSetter,
                       boolean setterCanReturnItsClass, boolean ignoreAbstractMethods, Set<Token> tokens) {
        super("checkstyle.HiddenField");
        this.ignoreFormat = ignoreFormat;
        this.ignoreConstructorParameter = ignoreConstructorParameter;
        this.ignoreSetter = ignoreSetter;
        this.setterCanReturnItsClass = setterCanReturnItsClass;
        this.ignoreAbstractMethods = ignoreAbstractMethods;
        this.tokens = tokens;
        setCursoringOn();
    }

    @AutoConfigure
    public static HiddenField configure(Config config) {
        return fromModule(
                config,
                "HiddenField",
                m -> new HiddenField(
                        m.prop("ignoreFormat", null),
                        m.prop("ignoreConstructorParameter", false),
                        m.prop("ignoreSetter", false),
                        m.prop("setterCanReturnItsClass", false),
                        m.prop("ignoreAbstractMethods", false),
                        m.propAsTokens(Token.class, DEFAULT_TOKENS)
                )
        );
    }

    @Override
    public J visitClassDecl(J.ClassDecl classDecl) {
        List<JavaType.Var> visibleSupertypeMembers = getVisibleSupertypeMembers(classDecl.getType());
        List<J.VariableDecls.NamedVar> shadows = visibleSupertypeMembers.stream()
                .filter(member -> ignoreFormat == null || !ignoreFormat.matcher(member.getName()).matches())
                .flatMap(member -> new FindNameShadows(member.getName(), enclosingClass())
                        .visit(classDecl.getBody()).stream())
                .collect(toList());

        if (!shadows.isEmpty()) {
            shadows.forEach(shadow -> andThen(new RenameShadowedName(shadow, visibleSupertypeMembers)));
        }

        return super.visitClassDecl(classDecl);
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        Cursor parent = getCursor()
                .getParentOrThrow() // J.VariableDecls
                .getParentOrThrow() // J.Block
                .getParentOrThrow(); // maybe J.ClassDecl

        if (parent.getTree() instanceof J.ClassDecl && (ignoreFormat == null || !ignoreFormat.matcher(variable.getSimpleName()).matches())) {
            J.ClassDecl classDecl = parent.getTree();
            List<J.VariableDecls.NamedVar> shadows = new FindNameShadows(variable, enclosingClass()).visit(enclosingBlock());
            if (!shadows.isEmpty()) {
                shadows.forEach(shadow -> andThen(new RenameShadowedName(shadow, getVisibleSupertypeMembers(classDecl.getType()))));
            }
        }

        return super.visitVariable(variable);
    }

    private class FindNameShadows extends JavaSourceVisitor<List<J.VariableDecls.NamedVar>> {
        @Nullable
        private final J.VariableDecls.NamedVar thatLookLike;

        private final String thatLookLikeName;
        private final J.ClassDecl enclosingClass;

        public FindNameShadows(J.VariableDecls.NamedVar thatLookLike, J.ClassDecl enclosingClass) {
            super("checkstyle.FindNameShadows");
            this.enclosingClass = enclosingClass;
            this.thatLookLike = thatLookLike;
            this.thatLookLikeName = thatLookLike.getSimpleName();
            setCursoringOn();
        }

        public FindNameShadows(String thatLookLikeName, J.ClassDecl enclosingClass) {
            super("checkstyle.FindNameShadows");
            this.thatLookLike = null;
            this.thatLookLikeName = thatLookLikeName;
            this.enclosingClass = enclosingClass;
            setCursoringOn();
        }

        @Override
        public List<J.VariableDecls.NamedVar> defaultTo(Tree t) {
            return emptyList();
        }

        @Override
        public List<J.VariableDecls.NamedVar> visitClassDecl(J.ClassDecl classDecl) {
            // don't go into static inner classes, interfaces, or enums which have a different name scope
            if (!(classDecl.getKind() instanceof J.ClassDecl.Kind.Class) || classDecl.hasModifier("static")) {
                return emptyList();
            }
            return super.visitClassDecl(classDecl);
        }

        @Override
        public List<J.VariableDecls.NamedVar> visitVariable(J.VariableDecls.NamedVar variable) {
            List<J.VariableDecls.NamedVar> shadows = super.visitVariable(variable);

            Tree maybeMethodDecl = getCursor()
                    .getParentOrThrow() // J.VariableDecls
                    .getParentOrThrow() // maybe J.MethodDecl
                    .getTree();

            boolean isIgnorableConstructorParam = ignoreConstructorParameter;
            if (isIgnorableConstructorParam) {
                isIgnorableConstructorParam = maybeMethodDecl instanceof J.MethodDecl && ((J.MethodDecl) maybeMethodDecl).isConstructor();
            }

            boolean isIgnorableSetter = ignoreSetter;
            if (isIgnorableSetter &= maybeMethodDecl instanceof J.MethodDecl) {
                J.MethodDecl methodDecl = (J.MethodDecl) maybeMethodDecl;
                String methodName = methodDecl.getSimpleName();

                isIgnorableSetter = methodName.startsWith("set") &&
                        methodDecl.getReturnTypeExpr() != null &&
                        (setterCanReturnItsClass ?
                                enclosingClass.getType().equals(methodDecl.getReturnTypeExpr().getType()) :
                                JavaType.Primitive.Void.equals(methodDecl.getReturnTypeExpr().getType())) &&
                        (methodName.length() > 3 && variable.getSimpleName().equalsIgnoreCase(methodName.substring(3)));
            }

            boolean isIgnorableAbstractMethod = ignoreAbstractMethods;
            if (isIgnorableAbstractMethod) {
                isIgnorableAbstractMethod = maybeMethodDecl instanceof J.MethodDecl && ((J.MethodDecl) maybeMethodDecl).hasModifier("abstract");
            }

            if (variable != thatLookLike &&
                    !isIgnorableSetter &&
                    !isIgnorableConstructorParam &&
                    !isIgnorableAbstractMethod &&
                    variable.getSimpleName().equals(thatLookLikeName) &&
                    tokens.stream().anyMatch(t -> t.equals(Token.LAMBDA) ?
                            getCursor().getParentOrThrow().getTree() instanceof J.Lambda.Parameters :
                            t.getMatcher().matches(getCursor()))) {
                shadows.add(variable);
            }
            return shadows;
        }
    }

    private static class ShadowsName extends JavaSourceVisitor<Boolean> {
        private final Cursor scope;
        private final String name;

        private ShadowsName(Cursor scope, String name) {
            super("checkstyle.ShadowsName");
            this.scope = scope;
            this.name = name;
            setCursoringOn();
        }

        @Override
        public Boolean defaultTo(Tree t) {
            return false;
        }

        @Override
        public Boolean visitVariable(J.VariableDecls.NamedVar variable) {
            return (variable != scope.getTree() &&
                    isInSameNameScope(scope) &&
                    variable.getSimpleName().equals(name)
            ) || super.visitVariable(variable);
        }
    }

    private static class RenameShadowedName extends JavaRefactorVisitor {
        private final J.VariableDecls.NamedVar scope;
        private final List<JavaType.Var> supertypeMembers;

        public RenameShadowedName(J.VariableDecls.NamedVar scope, List<JavaType.Var> supertypeMembers) {
            super("checkstyle.RenameShadowedName");
            this.scope = scope;
            this.supertypeMembers = supertypeMembers;
            setCursoringOn();
        }

        @Override
        public J visitVariable(J.VariableDecls.NamedVar variable) {
            J.VariableDecls.NamedVar v = refactor(variable, super::visitVariable);

            if (scope.isScope(variable)) {
                String nextName = nextName(v.getSimpleName());
                while (matchesSupertypeMember(nextName) ||
                        new ShadowsName(getCursor(), nextName).visit(enclosingCompilationUnit())) {
                    nextName = nextName(nextName);
                }
                v = v.withName(v.getName().withName(nextName));
            }

            return v;
        }

        private boolean matchesSupertypeMember(String nextName) {
            return supertypeMembers.stream().anyMatch(m -> m.getName().equals(nextName));
        }

        private String nextName(String name) {
            Matcher nameMatcher = NAME_PATTERN.matcher(name);
            return nameMatcher.matches() ?
                    nameMatcher.group(1) + (Integer.parseInt(nameMatcher.group(2)) + 1) :
                    name + "1";
        }
    }
}
