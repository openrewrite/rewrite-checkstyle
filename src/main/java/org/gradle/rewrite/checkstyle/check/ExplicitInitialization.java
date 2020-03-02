package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;

import static org.openrewrite.Formatting.formatLastSuffix;
import static org.openrewrite.Formatting.stripSuffix;

public class ExplicitInitialization extends JavaRefactorVisitor {
    private final boolean onlyObjectReferences;

    public ExplicitInitialization(boolean onlyObjectReferences) {
        this.onlyObjectReferences = onlyObjectReferences;
    }

    public ExplicitInitialization() {
        this(false);
    }

    @Override
    public String getName() {
        return "checkstyle.ExplicitInitialization";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        J.VariableDecls.NamedVar v = refactor(variable, super::visitVariable);

        if (!(getCursor().getParentOrThrow() // J.VariableDecls
                .getParentOrThrow() // maybe J.Block
                .getParentOrThrow() // maybe J.ClassDecl
                .getTree() instanceof J.ClassDecl)) {
            return v;
        }

        JavaType.Primitive primitive = TypeUtils.asPrimitive(variable.getType());
        JavaType.Array array = TypeUtils.asArray(variable.getType());

        J.Literal literalInit = variable.getInitializer() instanceof J.Literal ? (J.Literal) variable.getInitializer() : null;

        if (literalInit != null) {
            if (TypeUtils.asClass(variable.getType()) != null && JavaType.Primitive.Null.equals(literalInit.getType())) {
                v = v.withInitializer(null).withName(stripSuffix(v.getName()));
            } else if (primitive != null && !onlyObjectReferences) {
                switch (primitive) {
                    case Boolean:
                        if (literalInit.getValue() == Boolean.valueOf(false)) {
                            v = v.withInitializer(null).withName(stripSuffix(v.getName()));
                        }
                        break;
                    case Char:
                    case Int:
                    case Long:
                    case Short:
                        if (literalInit.getValue() != null && ((Number) literalInit.getValue()).intValue() == 0) {
                            v = v.withInitializer(null).withName(stripSuffix(v.getName()));
                        }
                        break;
                }
            } else if (array != null && JavaType.Primitive.Null.equals(literalInit.getType())) {
                v = v.withInitializer(null)
                        .withDimensionsAfterName(formatLastSuffix(v.getDimensionsAfterName(), ""));
            }
        }

        return v;
    }
}
