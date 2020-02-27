package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.*;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.ArrayList;
import java.util.List;

import static org.openrewrite.tree.Formatting.format;
import static org.openrewrite.tree.J.randomId;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class CovariantEquals extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.CovariantEquals";
    }

    @Override
    public List<AstTransform> visitMethod(J.MethodDecl method) {
        Type.Class classType = TypeUtils.asClass(getCursor().enclosingClass().getType());
        return maybeTransform(method,
                method.getSimpleName().equals("equals") &&
                        method.hasModifier("public") &&
                        method.getReturnTypeExpr() != null &&
                        Type.Primitive.Boolean.equals(method.getReturnTypeExpr().getType()) &&
                        method.getParams().getParams().size() == 1 &&
                        method.getParams().getParams().stream().allMatch(p -> p.hasClassType(classType)),
                super::visitMethod,
                (m, cursor) -> {
                    String methodPrefix = methodFormattingPrefix(m);
                    String methodIndent = formatter().findIndent(cursor.enclosingBlock().getIndent(), method).getPrefix();

                    J.MethodDecl fixedMethod = m;

                    fixedMethod = maybeAddOverrideAnnotation(fixedMethod, methodPrefix, methodIndent);

                    if (!fixedMethod.getModifiers().isEmpty()) {
                        fixedMethod = fixedMethod.withModifiers(Formatting.formatFirstPrefix(fixedMethod.getModifiers(), methodIndent));
                    }

                    J.VariableDecls.NamedVar oldParamName = ((J.VariableDecls) m.getParams().getParams().iterator().next())
                            .getVars().iterator().next();
                    J.VariableDecls.NamedVar paramName = oldParamName
                            .withName(oldParamName.getName().withName("o".equals(oldParamName.getSimpleName()) ? "other" : "o"));

                    fixedMethod = changeParameterNameAndType(fixedMethod, paramName);
                    fixedMethod = addEqualsBody(fixedMethod, cursor, oldParamName, paramName);

                    return fixedMethod;
                }
        );
    }

    private J.MethodDecl addEqualsBody(J.MethodDecl method, Cursor cursor, J.VariableDecls.NamedVar oldParamName, J.VariableDecls.NamedVar paramName) {
        List<Statement> equalsBody = TreeBuilder.buildSnippet(cursor.enclosingCompilationUnit(), new Cursor(cursor, method.getBody()),
                "if (this == {}) return true;\n" +
                        "if ({} == null || getClass() != {}.getClass()) return false;\n" +
                        "Test {} = (Test) {};\n", paramName, paramName, paramName, oldParamName, paramName);

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
            annotations.add(new J.Annotation(randomId(), J.Ident.build(randomId(), "Override", Type.Class.build("java.lang.Override"), Formatting.EMPTY),
                    null, method.getAnnotations().isEmpty() ? format(methodPrefix) : format(methodIndent)));

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
