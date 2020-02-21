package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Expression;
import com.netflix.rewrite.tree.Flag;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Type;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;

import java.util.List;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.EMPTY;
import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.singletonList;

public class StringLiteralEquality extends RefactorVisitor {
    @Override
    public String getRuleName() {
        return "checkstyle.StringLiteralEquality";
    }

    @Override
    public List<AstTransform> visitBinary(Tr.Binary binary) {
        return maybeTransform(binary,
                binary.getOperator() instanceof Tr.Binary.Operator.Equal && (
                        isStringLiteral(binary.getLeft()) || isStringLiteral(binary.getRight())),
                super::visitBinary,
                b -> (Expression) b,
                b -> {
                    Tr.Binary binary2 = (Tr.Binary) b;
                    Expression left = isStringLiteral(binary.getRight()) ? binary.getRight() : binary.getLeft();
                    Expression right = isStringLiteral(binary.getRight()) ? binary.getLeft() : binary.getRight();

                    return new Tr.MethodInvocation(randomId(),
                            left.withFormatting(EMPTY),
                            null,
                            Tr.Ident.build(randomId(), "equals", Type.Primitive.Boolean, EMPTY),
                            new Tr.MethodInvocation.Arguments(randomId(), singletonList(right.withFormatting(EMPTY)), EMPTY),
                            Type.Method.build(Type.Class.build("java.lang.Object"), "equals",
                                    null, null, singletonList("o"),
                                    Set.of(Flag.Public)),
                            binary2.getFormatting());
                }
        );
    }

    public boolean isStringLiteral(Expression expression) {
        return expression instanceof Tr.Literal && ((Tr.Literal) expression).getType() == Type.Primitive.String;
    }
}
