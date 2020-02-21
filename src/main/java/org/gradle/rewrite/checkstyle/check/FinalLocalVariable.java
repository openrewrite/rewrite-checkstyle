package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.visitor.search.FindReferencesToVariable;

import java.util.ArrayList;
import java.util.List;

import static com.netflix.rewrite.tree.Formatting.*;
import static com.netflix.rewrite.tree.Tr.randomId;

public class FinalLocalVariable extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.FinalLocalVariable";
    }

    @Override
    public List<AstTransform> visitMultiVariable(Tr.VariableDecls multiVariable) {
        Tree variableScope = getCursor().getParentOrThrow().getTree();

        if (variableScope instanceof Tr.ClassDecl) {
            // we don't care about fields here
            super.visitMultiVariable(multiVariable);
        }

        return maybeTransform(multiVariable,
                !multiVariable.hasModifier("final") && multiVariable.getVars().stream()
                        .anyMatch(variable -> new FindReferencesToVariable(variable.getName()).visit(variableScope).size() +
                                (variable.getInitializer() == null ? -1 : 0) <= 0),
                super::visitMultiVariable,
                mv -> {
                    List<Tr.Modifier> modifiers = new ArrayList<>();
                    modifiers.add(new Tr.Modifier.Final(randomId(), mv.getTypeExpr() == null ? EMPTY :
                            format(mv.getTypeExpr().getFormatting().getPrefix())));
                    modifiers.addAll(formatFirstPrefix(mv.getModifiers(), " "));

                    return mv.withModifiers(modifiers).withTypeExpr(mv.getTypeExpr().withPrefix(" "));
                }
        );
    }
}
