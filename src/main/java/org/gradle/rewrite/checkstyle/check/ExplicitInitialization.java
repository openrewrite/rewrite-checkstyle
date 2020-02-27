package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.J;
import org.openrewrite.tree.Type;
import org.openrewrite.tree.TypeUtils;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.function.Function;

import static org.openrewrite.tree.Formatting.formatLastSuffix;
import static org.openrewrite.tree.Formatting.stripSuffix;

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
    public List<AstTransform> visitVariable(J.VariableDecls.NamedVar variable) {
        List<AstTransform> changes = super.visitVariable(variable);

        if(!(getCursor().getParentOrThrow() // J.VariableDecls
                .getParentOrThrow() // maybe J.Block
                .getParentOrThrow() // maybe J.ClassDecl
                .getTree() instanceof J.ClassDecl)) {
            return changes;
        }

        Type.Primitive primitive = TypeUtils.asPrimitive(variable.getType());
        Type.Array array = TypeUtils.asArray(variable.getType());

        J.Literal literalInit = variable.getInitializer() instanceof J.Literal ? (J.Literal) variable.getInitializer() : null;

        if (literalInit != null) {
            Function<J.VariableDecls.NamedVar, J.VariableDecls.NamedVar> removeInitializer = v -> v.withInitializer(null)
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
