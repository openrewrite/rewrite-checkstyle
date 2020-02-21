package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.TypeUtils;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.function.Function;

import static com.netflix.rewrite.tree.Formatting.formatLastSuffix;
import static com.netflix.rewrite.tree.Formatting.stripSuffix;

public class ExplicitInitialization extends RefactorVisitor {
    private final boolean onlyObjectReferences;

    public ExplicitInitialization(boolean onlyObjectReferences) {
        this.onlyObjectReferences = onlyObjectReferences;
    }

    public ExplicitInitialization() {
        this(false);
    }

    @Override
    public String getRuleName() {
        return "checkstyle.ExplicitInitialization";
    }

    @Override
    public List<AstTransform> visitVariable(Tr.VariableDecls.NamedVar variable) {
        List<AstTransform> changes = super.visitVariable(variable);

        if(!(getCursor().getParentOrThrow() // Tr.VariableDecls
                .getParentOrThrow() // maybe Tr.Block
                .getParentOrThrow() // maybe Tr.ClassDecl
                .getTree() instanceof Tr.ClassDecl)) {
            return changes;
        }

        Type.Primitive primitive = TypeUtils.asPrimitive(variable.getType());
        Type.Array array = TypeUtils.asArray(variable.getType());

        Tr.Literal literalInit = variable.getInitializer() instanceof Tr.Literal ? (Tr.Literal) variable.getInitializer() : null;

        if (literalInit != null) {
            Function<Tr.VariableDecls.NamedVar, Tr.VariableDecls.NamedVar> removeInitializer = v -> v.withInitializer(null)
                    .withName(stripSuffix(v.getName()));

            if (TypeUtils.asClass(variable.getType()) != null && Type.Primitive.Null.equals(literalInit.getType())) {
                changes.addAll(transform(variable, removeInitializer));
            } else if (primitive != null && !onlyObjectReferences) {
                switch (primitive) {
                    case Boolean:
                        if (literalInit.getValue() == Boolean.valueOf(false)) {
                            changes.addAll(transform(variable, removeInitializer));
                        }
                        break;
                    case Char:
                    case Int:
                    case Long:
                    case Short:
                        if (literalInit.getValue() != null && ((Number) literalInit.getValue()).intValue() == 0) {
                            changes.addAll(transform(variable, removeInitializer));
                        }
                        break;
                }
            } else if (array != null && Type.Primitive.Null.equals(literalInit.getType())) {
                changes.addAll(transform(variable, v -> v.withInitializer(null)
                        .withDimensionsAfterName(formatLastSuffix(v.getDimensionsAfterName(), ""))));
            }
        }

        return changes;
    }
}
