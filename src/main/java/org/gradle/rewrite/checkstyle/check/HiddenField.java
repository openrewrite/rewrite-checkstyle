package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.internal.lang.Nullable;
import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.visitor.CursorAstVisitor;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.ScopedRefactorVisitor;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.gradle.rewrite.checkstyle.policy.Token;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.stream.StreamSupport.stream;
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
        return "HiddenField";
    }

    @Override
    public List<AstTransform> visitVariable(Tr.VariableDecls.NamedVar variable) {
        Cursor parent = getCursor()
                .getParentOrThrow() // Tr.VariableDecls
                .getParentOrThrow() // Tr.Block
                .getParentOrThrow(); // maybe Tr.ClassDecl

        if (parent.getTree() instanceof Tr.ClassDecl) {
            List<Tr.VariableDecls.NamedVar> shadows = new FindNameShadows(variable).visit(getCursor().enclosingBlock());
            if (!shadows.isEmpty()) {
                shadows.forEach(shadow -> andThen(new RenameShadowedName(shadow.getId())));
            }
        }

        return super.visitVariable(variable);
    }

    @RequiredArgsConstructor
    private class FindNameShadows extends CursorAstVisitor<List<Tr.VariableDecls.NamedVar>> {
        private final Tr.VariableDecls.NamedVar thatLookLike;

        @Override
        public List<Tr.VariableDecls.NamedVar> defaultTo(Tree t) {
            return emptyList();
        }

        @Override
        public List<Tr.VariableDecls.NamedVar> visitVariable(Tr.VariableDecls.NamedVar variable) {
            List<Tr.VariableDecls.NamedVar> shadows = super.visitVariable(variable);

            if (variable != thatLookLike && variable.getSimpleName().equals(thatLookLike.getSimpleName()) &&
                    tokens.stream().anyMatch(t -> t.equals(LAMBDA) ?
                            getCursor().getParentOrThrow().getTree() instanceof Tr.Lambda.Parameters :
                            t.getMatcher().matches(variable, getCursor().getParentOrThrow()))) {
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
        public Boolean visitVariable(Tr.VariableDecls.NamedVar variable) {
            return (
                    variable != scope.getTree() &&
                    getCursor().isInSameNameScope(scope) &&
                    variable.getName().getSimpleName().equals(name)
            ) || super.visitVariable(variable);
        }
    }

    private static class RenameShadowedName extends ScopedRefactorVisitor {
        public RenameShadowedName(UUID scope) {
            super(scope);
        }

        @Override
        public List<AstTransform> visitVariable(Tr.VariableDecls.NamedVar variable) {
            return maybeTransform(variable.getId().equals(scope),
                    super.visitVariable(variable),
                    transform(variable, (v, cursor) -> {
                        String nextName = nextName(v.getSimpleName());
                        while (new ShadowsName(cursor, nextName).visit(cursor.enclosingCompilationUnit())) {
                            nextName = nextName(nextName);
                        }
                        return v.withName(v.getName().withName(nextName));
                    })
            );
        }

        private String nextName(String name) {
            Matcher nameMatcher = NAME_PATTERN.matcher(name);
            return nameMatcher.matches() ?
                    nameMatcher.group(1) + (Integer.parseInt(nameMatcher.group(2)) + 1) :
                    name + "1";
        }
    }
}
