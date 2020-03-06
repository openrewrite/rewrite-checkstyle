package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.Cursor;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.*;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.openrewrite.Formatting.*;
import static org.openrewrite.Tree.randomId;

public class CovariantEquals extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.CovariantEquals";
    }

    @Override
    public boolean isCursored() {
        return true;
    }

    @Override
    public J visitMethod(J.MethodDecl method) {
        J.MethodDecl m = refactor(method, super::visitMethod);
        JavaType.Class classType = TypeUtils.asClass(enclosingClass().getType());

        if (method.getSimpleName().equals("equals") &&
                method.hasModifier("public") &&
                method.getReturnTypeExpr() != null &&
                JavaType.Primitive.Boolean.equals(method.getReturnTypeExpr().getType()) &&
                method.getParams().getParams().size() == 1 &&
                method.getParams().getParams().stream().allMatch(p -> p.hasClassType(classType))) {

            String methodPrefix = methodFormattingPrefix(method);
            String methodIndent = formatter.findIndent(enclosingBlock().getIndent(), method).getPrefix();

            m = maybeAddOverrideAnnotation(m, methodPrefix, methodIndent);

            if (!m.getModifiers().isEmpty()) {
                m = m.withModifiers(formatFirstPrefix(m.getModifiers(), methodIndent));
            }

            J.VariableDecls.NamedVar oldParamName = ((J.VariableDecls) method.getParams().getParams().iterator().next())
                    .getVars().iterator().next();
            J.VariableDecls.NamedVar paramName = oldParamName
                    .withName(oldParamName.getName().withName("o".equals(oldParamName.getSimpleName()) ? "other" : "o"));

            m = changeParameterNameAndType(m, paramName);
            m = addEqualsBody(m, oldParamName, paramName);
        }

        return m;
    }

    private J.MethodDecl addEqualsBody(J.MethodDecl method, J.VariableDecls.NamedVar oldParamName, J.VariableDecls.NamedVar paramName) {
        String paramNameStr = paramName.printTrimmed();
        List<Statement> equalsBody = TreeBuilder.buildSnippet(enclosingCompilationUnit(), new Cursor(getCursor(), method.getBody()),
                "if (this == " + paramNameStr + ") return true;\n" +
                        "if (" + paramNameStr + " == null || getClass() != " + paramNameStr + ".getClass()) return false;\n" +
                        "Test " + oldParamName.printTrimmed() + " = (Test) " + paramNameStr + ";\n");

        //noinspection ConstantConditions
        equalsBody.addAll(method.getBody().getStatements());

        return method.withBody(method.getBody().withStatements(equalsBody));
    }

    /**
     * Change the parameter type to Object and the name to other 'other' or 'o'.
     */
    private J.MethodDecl changeParameterNameAndType(J.MethodDecl method, J.VariableDecls.NamedVar paramName) {
        return method.withParams(method.getParams().withParams(method.getParams().getParams().stream()
                .map(p -> ((J.VariableDecls) p)
                        .withVars(singletonList(paramName))
                        .withTypeExpr(TreeBuilder.buildName("Object")))
                .collect(toList())));
    }

    private J.MethodDecl maybeAddOverrideAnnotation(J.MethodDecl method, String methodPrefix, String methodIndent) {
        if (method.getAnnotations().stream().noneMatch(ann -> TypeUtils.isOfClassType(ann.getType(), "java.lang.Override"))) {
            List<J.Annotation> annotations = new ArrayList<>(method.getAnnotations());
            annotations.add(
                    new J.Annotation(
                            randomId(),
                            J.Ident.build(randomId(), "Override", JavaType.Class.build("java.lang.Override"), EMPTY),
                            null,
                            method.getAnnotations().isEmpty() ? format(methodPrefix) : format(methodIndent)
                    )
            );

            return method.withAnnotations(annotations);
        }
        return method;
    }

    private String methodFormattingPrefix(J.MethodDecl method) {
        if (!method.getAnnotations().isEmpty()) {
            return method.getAnnotations().iterator().next().getFormatting().getPrefix();
        }

        // we know this method is public, so there must be a modifier;
        return method.getModifiers().iterator().next().getFormatting().getPrefix();
    }
}
