package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.apache.commons.collections.StaticBucketMap;

import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

/**
 * Fix for <a href="https://checkstyle.sourceforge.io/config_naming.html#StaticVariableName">StaticVariableName</a>.
 */
@Builder
public class StaticVariableName extends RefactorVisitor<Tr.VariableDecls> {
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
    protected String getRuleName() {
        return "StaticVariableName";
    }

    @Override
    public List<AstTransform<Tr.VariableDecls>> visitMultiVariable(Tr.VariableDecls multiVariable) {
        if (!multiVariable.hasModifier("static") || !(
                (applyToPublic && multiVariable.hasModifier("public")) ||
                        (applyToProtected && multiVariable.hasModifier("protected")) ||
                        (applyToPrivate && multiVariable.hasModifier("private")) ||
                        (applyToPackage && (!multiVariable.hasModifier("public") && !multiVariable.hasModifier("protected") && !multiVariable.hasModifier("private")))
        )) {
            return emptyList();
        }

        if (multiVariable.getVars().stream().anyMatch(v -> !v.getSimpleName().matches(format))) {
            return transform(mv -> mv.withVars(mv.getVars().stream()
                    .map(v -> v.getSimpleName().matches(format) ? v :
                        v.withName(v.getName().withName(renamer.apply(v.getSimpleName()))))
                    .collect(toList())));
        }

        return emptyList();
    }

    static String snakeCaseToCamel(String value) {
        if(!value.matches("([A-Z0-9]+_*)+")) {
            return value;
        }

        StringBuilder camelName = new StringBuilder();
        char last = 0;
        for (char c : value.toCharArray()) {
            if(c != '_') {
                camelName.append(last == '_' ? c : Character.toLowerCase(c));
            }
            last = c;
        }
        return camelName.toString();
    }
}
