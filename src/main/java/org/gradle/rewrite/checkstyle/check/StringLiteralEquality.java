package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Set;

import static java.util.Collections.singletonList;
import static org.openrewrite.Formatting.EMPTY;
import static org.openrewrite.Tree.randomId;

public class StringLiteralEquality extends JavaRefactorVisitor {
    @Override
    public String getName() {
        return "checkstyle.StringLiteralEquality";
    }

    @Override
    public J visitBinary(J.Binary binary) {
        if(binary.getOperator() instanceof J.Binary.Operator.Equal && (
                isStringLiteral(binary.getLeft()) || isStringLiteral(binary.getRight()))) {
            Expression left = isStringLiteral(binary.getRight()) ? binary.getRight() : binary.getLeft();
            Expression right = isStringLiteral(binary.getRight()) ? binary.getLeft() : binary.getRight();

            return new J.MethodInvocation(randomId(),
                    left.withFormatting(EMPTY),
                    null,
                    J.Ident.build(randomId(), "equals", JavaType.Primitive.Boolean, EMPTY),
                    new J.MethodInvocation.Arguments(randomId(), singletonList(right.withFormatting(EMPTY)), EMPTY),
                    JavaType.Method.build(JavaType.Class.build("java.lang.Object"), "equals",
                            null, null, singletonList("o"),
                            Set.of(Flag.Public)),
                    binary.getFormatting());
        }

        return super.visitBinary(binary);
    }

    public boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == JavaType.Primitive.String;
    }
}
