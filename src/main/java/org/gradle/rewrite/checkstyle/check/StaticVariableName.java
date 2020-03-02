package org.gradle.rewrite.checkstyle.check;

import lombok.Builder;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.java.visitor.refactor.ChangeFieldName;
import org.openrewrite.java.visitor.refactor.JavaRefactorVisitor;

import java.util.function.Function;

/**
 * Fix for <a href="https://checkstyle.sourceforge.io/config_naming.html#StaticVariableName">StaticVariableName</a>.
 */
@Builder
public class StaticVariableName extends JavaRefactorVisitor {
    @Builder.Default
    private String format = "^[a-z][a-zA-Z0-9]*$";

    @Builder.Default
    private final Function<String, String> renamer = StaticVariableName::snakeCaseToCamel;

    @Builder.Default
    private final boolean applyToPublic = true;

    @Builder.Default
    private final boolean applyToProtected = true;

    @Builder.Default
    private final boolean applyToPackage = true;

    @Builder.Default
    private final boolean applyToPrivate = true;

    @Override
    public String getName() {
        return "checkstyle.StaticVariableName";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        J.VariableDecls multiVariable = getCursor().getParentOrThrow().getTree();
        if(multiVariable.hasModifier("static") && (
                (applyToPublic && multiVariable.hasModifier("public")) ||
                        (applyToProtected && multiVariable.hasModifier("protected")) ||
                        (applyToPrivate && multiVariable.hasModifier("private")) ||
                        (applyToPackage && (!multiVariable.hasModifier("public") && !multiVariable.hasModifier("protected") && !multiVariable.hasModifier("private")))
        )) {
            JavaType.Class containingClassType = TypeUtils.asClass(enclosingClass().getType());
            andThen(new ChangeFieldName(containingClassType, variable.getSimpleName(), renamer.apply(variable.getSimpleName())));
        }
        return super.visitVariable(variable);
    }

    static String snakeCaseToCamel(String value) {
        if (!value.matches("([A-Z0-9]+_*)+")) {
            return value;
        }

        StringBuilder camelName = new StringBuilder();
        char last = 0;
        for (char c : value.toCharArray()) {
            if (c != '_') {
                camelName.append(last == '_' ? c : Character.toLowerCase(c));
            }
            last = c;
        }
        return camelName.toString();
    }
}
