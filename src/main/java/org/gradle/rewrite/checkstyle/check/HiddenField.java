package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.tree.*;
import org.openrewrite.visitor.CursorAstVisitor;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.refactor.ScopedRefactorVisitor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.openrewrite.tree.TypeUtils.getVisibleSupertypeMembers;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

@Builder
public class HiddenField extends RefactorVisitor {
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
    public String getRuleName() {
        return "checkstyle.HiddenField";
    }

    @Override
    public List<AstTransform> visitClassDecl(J.ClassDecl classDecl) {
        List<Type.Var> visibleSupertypeMembers = getVisibleSupertypeMembers(classDecl.getType());
        List<J.VariableDecls.NamedVar> shadows = visibleSupertypeMembers.stream()
                .filter(member -> ignoreFormat == null || !ignoreFormat.matcher(member.getName()).matches())
                .flatMap(member -> new FindNameShadows(member.getName(), getCursor().enclosingClass()).visit(classDecl.getBody()).stream())
                .collect(toList());

        if (!shadows.isEmpty()) {
            shadows.forEach(shadow -> andThen(new RenameShadowedName(shadow.getId(), visibleSupertypeMembers)));
        }

        return super.visitClassDecl(classDecl);
    }

    @Override
    public List<AstTransform> visitVariable(J.VariableDecls.NamedVar variable) {
        Cursor parent = getCursor()
                .getParentOrThrow() // J.VariableDecls
                .getParentOrThrow() // J.Block
                .getParentOrThrow(); // maybe J.ClassDecl

        if (parent.getTree() instanceof J.ClassDecl && (ignoreFormat == null || !ignoreFormat.matcher(variable.getSimpleName()).matches())) {
            J.ClassDecl classDecl = parent.getTree();
            List<J.VariableDecls.NamedVar> shadows = new FindNameShadows(variable, getCursor().enclosingClass()).visit(getCursor().enclosingBlock());
            if (!shadows.isEmpty()) {
                shadows.forEach(shadow -> andThen(new RenameShadowedName(shadow.getId(), getVisibleSupertypeMembers(classDecl.getType()))));
            }
        }

        return super.visitVariable(variable);
    }

    private class FindNameShadows extends CursorAstVisitor<List<J.VariableDecls.NamedVar>> {
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
                                Type.Primitive.Void.equals(methodDecl.getReturnTypeExpr().getType())) &&
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
    private static class ShadowsName extends CursorAstVisitor<Boolean> {
        private final Cursor scope;
        private final String name;

        @Override
        public Boolean defaultTo(Tree t) {
            return false;
        }

        @Override
        public Boolean visitVariable(J.VariableDecls.NamedVar variable) {
            return (variable != scope.getTree() &&
                    getCursor().isInSameNameScope(scope) &&
                    variable.getSimpleName().equals(name)
            ) || super.visitVariable(variable);
        }
    }

    private static class RenameShadowedName extends ScopedRefactorVisitor {
        private final List<Type.Var> supertypeMembers;

        public RenameShadowedName(UUID scope, List<Type.Var> supertypeMembers) {
            super(scope);
            this.supertypeMembers = supertypeMembers;
        }

        @Override
        public List<AstTransform> visitVariable(J.VariableDecls.NamedVar variable) {
            return transformIfScoped(variable,
                    super::visitVariable,
                    (v, cursor) -> {
                        String nextName = nextName(v.getSimpleName());
                        while (matchesSupertypeMember(nextName) ||
                                new ShadowsName(cursor, nextName).visit(cursor.enclosingCompilationUnit())) {
                            nextName = nextName(nextName);
                        }
                        return v.withName(v.getName().withName(nextName));
                    }
            );
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
