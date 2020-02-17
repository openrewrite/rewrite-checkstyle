package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.*;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.ScopedRefactorVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.netflix.rewrite.tree.Formatting.format;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class CovariantEquals extends RefactorVisitor {
    @Override
    public List<AstTransform> visitMethod(Tr.MethodDecl method) {
        Type.Class classType = TypeUtils.asClass(getCursor().enclosingClass().getType());

        if (method.getSimpleName().equals("equals") &&
                method.hasModifier("public") &&
                method.getReturnTypeExpr() != null &&
                Type.Primitive.Boolean.equals(method.getReturnTypeExpr().getType()) &&
                method.getParams().getParams().size() == 1 &&
                method.getParams().getParams().stream().allMatch(p -> p.hasClassType(classType))) {

            if (method.getAnnotations().stream().noneMatch(ann -> TypeUtils.isOfClassType(ann.getType(), "java.lang.Override"))) {
                andThen(new AddOverrideAnnotation(method.getId()));
            }
        }

        return super.visitMethod(method);
    }

    static class AddOverrideAnnotation extends ScopedRefactorVisitor {
        public AddOverrideAnnotation(UUID scope) {
            super(scope);
        }

        @Override
        public List<AstTransform> visitMethod(Tr.MethodDecl method) {
            return maybeTransform(scope.equals(method.getId()),
                    super.visitMethod(method),
                    transform(method, (m, cursor) -> {
                        String methodPrefix = methodFormattingPrefix(m);
                        String methodIndent = formatter().findIndent(cursor.enclosingBlock().getIndent(), method).getPrefix();

                        List<Tr.Annotation> annotations = new ArrayList<>(m.getAnnotations());
                        annotations.add(new Tr.Annotation(randomId(), Tr.Ident.build(randomId(), "Override", Type.Class.build("java.lang.Override"), Formatting.EMPTY),
                                null, m.getAnnotations().isEmpty() ? format(methodPrefix) : format(methodIndent)));

                        Tr.MethodDecl fixedMethod = m.withAnnotations(annotations);

                        if (!fixedMethod.getModifiers().isEmpty()) {
                            fixedMethod = fixedMethod.withModifiers(Formatting.formatFirstPrefix(fixedMethod.getModifiers(), methodIndent));
                        }

                        Tr.VariableDecls.NamedVar currentParamName = ((Tr.VariableDecls) m.getParams().getParams().iterator().next())
                                .getVars().iterator().next();
                        Tr.VariableDecls.NamedVar paramName =  currentParamName
                                .withName(currentParamName.getName().withName("o".equals(currentParamName.getSimpleName()) ? "other" : "o"));

                        fixedMethod = fixedMethod.withParams(m.getParams().withParams(m.getParams().getParams().stream()
                                .map(p -> ((Tr.VariableDecls) p)
                                        .withVars(singletonList(paramName))
                                        .withTypeExpr(TreeBuilder.buildName("Object")))
                                .collect(toList())));

                        List<Statement> equalsBody = TreeBuilder.buildSnippet(cursor.enclosingCompilationUnit(), new Cursor(cursor, method.getBody()),
                                "if (this == {}) return true;\n" +
                                        "if ({} == null || getClass() != {}.getClass()) return false;\n" +
                                        "Test {} = (Test) {};\n", paramName, paramName, paramName, currentParamName, paramName);

                        //noinspection ConstantConditions
                        equalsBody.addAll(fixedMethod.getBody().getStatements());

                        fixedMethod = fixedMethod.withBody(fixedMethod.getBody().withStatements(equalsBody));

                        return fixedMethod;
                    })
            );
        }

        private String methodFormattingPrefix(Tr.MethodDecl method) {
            if (!method.getAnnotations().isEmpty()) {
                return method.getAnnotations().iterator().next().getFormatting().getPrefix();
            }

            // we know this method is public, so there must be a modifier;
            return method.getModifiers().iterator().next().getFormatting().getPrefix();
        }
    }
}
