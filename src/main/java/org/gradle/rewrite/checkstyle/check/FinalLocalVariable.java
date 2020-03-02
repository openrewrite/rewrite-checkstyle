package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Tree;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.search.FindReferencesToVariable;
import org.openrewrite.java.tree.J;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

public class FinalLocalVariable extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.FinalLocalVariable";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitMultiVariable(J.VariableDecls multiVariable) {

        J.VariableDecls mv = refactor(multiVariable, super::visitMultiVariable);

        Tree variableScope = getCursor().getParentOrThrow().getParentOrThrow().getTree();
        if (variableScope instanceof J.ClassDecl) {
            // we don't care about fields here
            return mv;
        }

        if (!multiVariable.hasModifier("final") && multiVariable.getVars().stream()
                .anyMatch(variable -> new FindReferencesToVariable(variable.getName()).visit(variableScope).size() +
                        (variable.getInitializer() == null ? -1 : 0) <= 0)) {
            List<J.Modifier> modifiers = new ArrayList<>();
            modifiers.add(new J.Modifier.Final(randomId(), mv.getTypeExpr() == null ? EMPTY :
                    format(mv.getTypeExpr().getFormatting().getPrefix())));
            modifiers.addAll(formatFirstPrefix(mv.getModifiers(), " "));

            mv = mv.withModifiers(modifiers).withTypeExpr(mv.getTypeExpr().withPrefix(" "));
        }

        return mv;
    }
}
