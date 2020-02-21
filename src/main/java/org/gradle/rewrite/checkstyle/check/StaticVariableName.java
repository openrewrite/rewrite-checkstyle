package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.TypeUtils;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import lombok.Builder;

import java.util.List;
import java.util.function.Function;

/**
 * Fix for <a href="https://checkstyle.sourceforge.io/config_naming.html#StaticVariableName">StaticVariableName</a>.
 */
@Builder
public class StaticVariableName extends RefactorVisitor {
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
    public String getRuleName() {
        return "checkstyle.StaticVariableName";
    }

    @Override
    public List<AstTransform> visitVariable(Tr.VariableDecls.NamedVar variable) {
        Tr.VariableDecls multiVariable = getCursor().getParentOrThrow().getTree();
        if(multiVariable.hasModifier("static") && (
                (applyToPublic && multiVariable.hasModifier("public")) ||
                        (applyToProtected && multiVariable.hasModifier("protected")) ||
                        (applyToPrivate && multiVariable.hasModifier("private")) ||
                        (applyToPackage && (!multiVariable.hasModifier("public") && !multiVariable.hasModifier("protected") && !multiVariable.hasModifier("private")))
        )) {
            Type.Class containingClassType = TypeUtils.asClass(getCursor().enclosingClass().getType());
            changeFieldName(containingClassType, variable.getSimpleName(), renamer.apply(variable.getSimpleName()));
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
