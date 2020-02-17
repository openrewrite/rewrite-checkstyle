package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Formatting;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.tree.TypeUtils;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.netflix.rewrite.tree.visitor.refactor.ScopedRefactorVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.netflix.rewrite.tree.Formatting.format;
import static com.netflix.rewrite.tree.Tr.randomId;

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

        /*
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Test t = (Test) o;
            return n == t.n;
        }
        */

        return super.visitMethod(method);
    }

    class AddOverrideAnnotation extends ScopedRefactorVisitor {

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
