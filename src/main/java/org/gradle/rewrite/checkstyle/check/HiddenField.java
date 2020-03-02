package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaSourceVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.openrewrite.java.visitor.refactor.ScopedJavaRefactorVisitor;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openrewrite.java.tree.TypeUtils.getVisibleSupertypeMembers;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

@Builder
public class HiddenField extends JavaRefactorVisitor {
    private static final Pattern NAME_PATTERN = Pattern.compile("(.+)(\\d+)");

    @Builder.Default
    @Nullable
    Pattern ignoreFormat = null;

    @Builder.Default
    boolean ignoreConstructorParameter = false;

    @Builder.Default
    boolean ignoreSetter = false;

    @Builder.Default
    boolean setterCanReturnItsClass = false;

    @Builder.Default
    boolean ignoreAbstractMethods = false;

    @Builder.Default
    Set<Token> tokens = Set.of(VARIABLE_DEF, PARAMETER_DEF, LAMBDA);

    @Override
    public String getName() {
        return "checkstyle.HiddenField";
    }

    @Override
    public boolean isCursored() {
        return true;
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
            shadows.forEach(shadow -> andThen(new RenameShadowedName(shadow.getId(), visibleSupertypeMembers)));
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
                shadows.forEach(shadow -> andThen(new RenameShadowedName(shadow.getId(), getVisibleSupertypeMembers(classDecl.getType()))));
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
            this.thatLookLike = thatLookLike;
            this.thatLookLikeName = thatLookLike.getSimpleName();
            this.enclosingClass = enclosingClass;
        }

        public FindNameShadows(String thatLookLikeName, J.ClassDecl enclosingClass) {
            this.enclosingClass = enclosingClass;
            this.thatLookLike = null;
            this.thatLookLikeName = thatLookLikeName;
        }

        @Override
        public boolean isCursored() {
            return true;
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

                //noinspection ConstantConditions
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
                    tokens.stream().anyMatch(t -> t.equals(LAMBDA) ?
                            getCursor().getParentOrThrow().getTree() instanceof J.Lambda.Parameters :
                            t.getMatcher().matches(getCursor()))) {
                shadows.add(variable);
            }
            return shadows;
        }
    }

    @RequiredArgsConstructor
    private static class ShadowsName extends JavaSourceVisitor<Boolean> {
        private final Cursor scope;
        private final String name;

        @Override
        public boolean isCursored() {
            return true;
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

    private static class RenameShadowedName extends ScopedJavaRefactorVisitor {
        private final List<JavaType.Var> supertypeMembers;

        public RenameShadowedName(UUID scope, List<JavaType.Var> supertypeMembers) {
            super(scope);
            this.supertypeMembers = supertypeMembers;
        }

        @Override
        public J visitVariable(J.VariableDecls.NamedVar variable) {
            J.VariableDecls.NamedVar v = refactor(variable, super::visitVariable);

            if(isScope()) {
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
