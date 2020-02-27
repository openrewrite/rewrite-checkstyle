package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.tree.Expression;
import org.openrewrite.tree.Flag;
import org.openrewrite.tree.J;
import org.openrewrite.tree.Type;
import org.openrewrite.visitor.refactor.AstTransform;
import org.openrewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singletonList;
import static org.openrewrite.tree.Formatting.EMPTY;
import static org.openrewrite.tree.J.randomId;

public class StringLiteralEquality extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.StringLiteralEquality";
    }

    @Override
    public List<AstTransform> visitBinary(J.Binary binary) {
        return maybeTransform(binary,
                binary.getOperator() instanceof J.Binary.Operator.Equal && (
                        isStringLiteral(binary.getLeft()) || isStringLiteral(binary.getRight())),
                super::visitBinary,
                b -> (Expression) b,
                b -> {
                    J.Binary binary2 = (J.Binary) b;
                    Expression left = isStringLiteral(binary.getRight()) ? binary.getRight() : binary.getLeft();
                    Expression right = isStringLiteral(binary.getRight()) ? binary.getLeft() : binary.getRight();

                    return new J.MethodInvocation(randomId(),
                            left.withFormatting(EMPTY),
                            null,
                            J.Ident.build(randomId(), "equals", Type.Primitive.Boolean, EMPTY),
                            new J.MethodInvocation.Arguments(randomId(), singletonList(right.withFormatting(EMPTY)), EMPTY),
                            Type.Method.build(Type.Class.build("java.lang.Object"), "equals",
                                    null, null, singletonList("o"),
                                    Set.of(Flag.Public)),
                            binary2.getFormatting());
                }
        );
    }

    public boolean isStringLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getType() == Type.Primitive.String;
    }
}
