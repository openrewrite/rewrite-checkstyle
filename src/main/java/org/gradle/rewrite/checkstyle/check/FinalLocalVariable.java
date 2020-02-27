package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.J;
import org.openrewrite.tree.Tree;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;
import org.openrewrite.visitor.search.FindReferencesToVariable;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.tree.Formatting.*;
import static org.openrewrite.tree.J.randomId;

public class FinalLocalVariable extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.FinalLocalVariable";
    }

    @Override
    public List<AstTransform> visitMultiVariable(J.VariableDecls multiVariable) {
        Tree variableScope = getCursor().getParentOrThrow().getTree();

        if (variableScope instanceof J.ClassDecl) {
            // we don't care about fields here
            super.visitMultiVariable(multiVariable);
        }

        return maybeTransform(multiVariable,
                !multiVariable.hasModifier("final") && multiVariable.getVars().stream()
                        .anyMatch(variable -> new FindReferencesToVariable(variable.getName()).visit(variableScope).size() +
                                (variable.getInitializer() == null ? -1 : 0) <= 0),
                super::visitMultiVariable,
                mv -> {
                    List<J.Modifier> modifiers = new ArrayList<>();
                    modifiers.add(new J.Modifier.Final(randomId(), mv.getTypeExpr() == null ? EMPTY :
                            format(mv.getTypeExpr().getFormatting().getPrefix())));
                    modifiers.addAll(formatFirstPrefix(mv.getModifiers(), " "));

                    return mv.withModifiers(modifiers).withTypeExpr(mv.getTypeExpr().withPrefix(" "));
                }
        );
    }
}
