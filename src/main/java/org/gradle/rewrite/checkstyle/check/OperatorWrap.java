package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;
import com.netflix.rewrite.tree.TypeTree;
import com.netflix.rewrite.visitor.refactor.AstTransform;
import com.netflix.rewrite.visitor.refactor.RefactorVisitor;
import lombok.Builder;
import org.gradle.rewrite.checkstyle.policy.OperatorToken;
import org.gradle.rewrite.checkstyle.policy.WrapPolicy;

import java.util.List;
import java.util.Set;

import static com.netflix.rewrite.tree.Formatting.*;
import static org.gradle.rewrite.checkstyle.policy.OperatorToken.*;

@Builder
public class OperatorWrap extends RefactorVisitor {
    @Builder.Default
    private final WrapPolicy option = WrapPolicy.NL;

    @Builder.Default
    private final Set<OperatorToken> tokens = Set.of(
            QUESTION,
            COLON,
            EQUAL,
            NOT_EQUAL,
            DIV,
            PLUS,
            MINUS,
            STAR,
            MOD,
            SR,
            BSR,
            GE,
            GT,
            SL,
            LE,
            LT,
            BXOR,
            BOR,
            LOR,
            BAND,
            LAND,
            TYPE_EXTENSION_AND,
            LITERAL_INSTANCEOF
    );

    @Override
    public String getRuleName() {
        return "OperatorWrap{policy=" + option + "}";
    }

    @Override
    public List<AstTransform> visitBinary(Tr.Binary binary) {
        List<AstTransform> changes = super.visitBinary(binary);
        Tr.Binary.Operator op = binary.getOperator();

        if ((tokens.contains(DIV) && op instanceof Tr.Binary.Operator.Division) ||
                (tokens.contains(STAR) && op instanceof Tr.Binary.Operator.Multiplication) ||
                (tokens.contains(PLUS) && op instanceof Tr.Binary.Operator.Addition) ||
                (tokens.contains(MINUS) && op instanceof Tr.Binary.Operator.Subtraction) ||
                (tokens.contains(MOD) && op instanceof Tr.Binary.Operator.Modulo) ||
                (tokens.contains(SR) && op instanceof Tr.Binary.Operator.RightShift) ||
                (tokens.contains(SL) && op instanceof Tr.Binary.Operator.LeftShift) ||
                (tokens.contains(BSR) && op instanceof Tr.Binary.Operator.UnsignedRightShift) ||
                (tokens.contains(EQUAL) && op instanceof Tr.Binary.Operator.Equal) ||
                (tokens.contains(NOT_EQUAL) && op instanceof Tr.Binary.Operator.NotEqual) ||
                (tokens.contains(GT) && op instanceof Tr.Binary.Operator.GreaterThan) ||
                (tokens.contains(GE) && op instanceof Tr.Binary.Operator.GreaterThanOrEqual) ||
                (tokens.contains(LT) && op instanceof Tr.Binary.Operator.LessThan) ||
                (tokens.contains(LE) && op instanceof Tr.Binary.Operator.LessThanOrEqual) ||
                (tokens.contains(BAND) && op instanceof Tr.Binary.Operator.BitAnd) ||
                (tokens.contains(BXOR) && op instanceof Tr.Binary.Operator.BitXor) ||
                (tokens.contains(BOR) && op instanceof Tr.Binary.Operator.BitOr) ||
                (tokens.contains(LAND) && op instanceof Tr.Binary.Operator.And) ||
                (tokens.contains(LOR) && op instanceof Tr.Binary.Operator.Or)) {

            if (option == WrapPolicy.NL) {
                if (binary.getRight().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(binary, b -> b
                            .withOperator(b.getOperator().withPrefix(b.getRight().getFormatting().getPrefix()))
                            .withRight(b.getRight().withPrefix(" "))));
                }
            } else if (op.getFormatting().getPrefix().contains("\n")) {
                changes.addAll(transform(binary, b -> b
                        .withOperator(b.getOperator().withPrefix(" "))
                        .withRight(b.getRight().withPrefix(op.getFormatting().getPrefix()))));
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitTypeParameter(Tr.TypeParameter typeParam) {
        List<AstTransform> changes = super.visitTypeParameter(typeParam);

        if (tokens.contains(TYPE_EXTENSION_AND) && typeParam.getBounds() != null) {
            List<TypeTree> types = typeParam.getBounds().getTypes();
            for (int i = 0; i < types.size() - 1; i++) {
                TypeTree tp1 = types.get(i);
                TypeTree tp2 = types.get(i + 1);

                if (option == WrapPolicy.NL) {
                    if (tp2.getFormatting().getPrefix().contains("\n")) {
                        changes.addAll(transform(tp1, t -> t.withSuffix(tp2.getFormatting().getPrefix())));
                        changes.addAll(transform(tp2, t -> t.withPrefix(" ")));
                    }
                } else if (tp1.getFormatting().getSuffix().contains("\n")) {
                    changes.addAll(transform(tp1, t -> t.withSuffix(" ")));
                    changes.addAll(transform(tp2, t -> t.withPrefix(tp1.getFormatting().getSuffix())));
                }
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitInstanceOf(Tr.InstanceOf instanceOf) {
        List<AstTransform> changes = super.visitInstanceOf(instanceOf);

        if (tokens.contains(LITERAL_INSTANCEOF)) {
            if (option == WrapPolicy.NL) {
                if (instanceOf.getClazz().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(instanceOf, i -> i
                            .withExpr(i.getExpr().withSuffix(i.getClazz().getFormatting().getPrefix()))
                            .withClazz(i.getClazz().withPrefix(" "))));
                }
            } else if (instanceOf.getExpr().getFormatting().getSuffix().contains("\n")) {
                changes.addAll(transform(instanceOf, i -> i
                        .withExpr(i.getExpr().withSuffix(" "))
                        .withClazz(i.getClazz().withPrefix(i.getExpr().getFormatting().getSuffix()))));
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitTernary(Tr.Ternary ternary) {
        List<AstTransform> changes = super.visitTernary(ternary);

        if (tokens.contains(QUESTION)) {
            if (option == WrapPolicy.NL) {
                if (ternary.getTruePart().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(ternary, t -> t
                            .withCondition(t.getCondition().withSuffix(t.getTruePart().getFormatting().getPrefix()))
                            .withTruePart(t.getTruePart().withPrefix(" "))));
                }
            } else if (ternary.getCondition().getFormatting().getSuffix().contains("\n")) {
                changes.addAll(transform(ternary, t -> t
                        .withCondition(t.getCondition().withSuffix(" "))
                        .withTruePart(t.getTruePart().withPrefix(t.getCondition().getFormatting().getSuffix()))));
            }
        }

        if (tokens.contains(COLON)) {
            if (option == WrapPolicy.NL) {
                if (ternary.getFalsePart().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(ternary, t -> t
                            .withTruePart(t.getTruePart().withSuffix(t.getFalsePart().getFormatting().getPrefix()))
                            .withFalsePart(t.getFalsePart().withPrefix(" "))));
                }
            } else if (ternary.getTruePart().getFormatting().getSuffix().contains("\n")) {
                changes.addAll(transform(ternary, t -> t
                        .withTruePart(t.getTruePart().withSuffix(" "))
                        .withFalsePart(t.getFalsePart().withPrefix(t.getTruePart().getFormatting().getSuffix()))));
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitAssignOp(Tr.AssignOp assignOp) {
        List<AstTransform> changes = super.visitAssignOp(assignOp);

        Tr.AssignOp.Operator op = assignOp.getOperator();

        if ((tokens.contains(PLUS_ASSIGN) && op instanceof Tr.AssignOp.Operator.Addition) ||
                (tokens.contains(MINUS_ASSIGN) && op instanceof Tr.AssignOp.Operator.Subtraction) ||
                (tokens.contains(STAR_ASSIGN) && op instanceof Tr.AssignOp.Operator.Multiplication) ||
                (tokens.contains(DIV_ASSIGN) && op instanceof Tr.AssignOp.Operator.Division) ||
                (tokens.contains(MOD_ASSIGN) && op instanceof Tr.AssignOp.Operator.Modulo) ||
                (tokens.contains(SR_ASSIGN) && op instanceof Tr.AssignOp.Operator.RightShift) ||
                (tokens.contains(SL_ASSIGN) && op instanceof Tr.AssignOp.Operator.LeftShift) ||
                (tokens.contains(BSR_ASSIGN) && op instanceof Tr.AssignOp.Operator.UnsignedRightShift) ||
                (tokens.contains(BAND_ASSIGN) && op instanceof Tr.AssignOp.Operator.BitAnd) ||
                (tokens.contains(BXOR_ASSIGN) && op instanceof Tr.AssignOp.Operator.BitXor) ||
                (tokens.contains(BOR_ASSIGN) && op instanceof Tr.AssignOp.Operator.BitOr)) {

            if (option == WrapPolicy.NL) {
                if (assignOp.getAssignment().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(assignOp, a -> a
                            .withOperator(a.getOperator().withPrefix(a.getAssignment().getFormatting().getPrefix()))
                            .withAssignment(a.getAssignment().withPrefix(" "))));
                }
            } else if (op.getFormatting().getPrefix().contains("\n")) {
                changes.addAll(transform(assignOp, a -> a
                        .withOperator(a.getOperator().withPrefix(" "))
                        .withAssignment(a.getAssignment().withPrefix(op.getFormatting().getPrefix()))));
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitMemberReference(Tr.MemberReference memberRef) {
        List<AstTransform> changes = super.visitMemberReference(memberRef);

        if (tokens.contains(METHOD_REF)) {
            if (option == WrapPolicy.NL) {
                if (memberRef.getReference().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(memberRef, mr -> mr
                            .withContaining(mr.getContaining().withSuffix(mr.getReference().getFormatting().getPrefix()))
                            .withReference(stripPrefix(mr.getReference()))));
                }
            } else if (memberRef.getContaining().getFormatting().getSuffix().contains("\n")) {
                changes.addAll(transform(memberRef, mr -> mr
                        .withContaining(stripSuffix(mr.getContaining()))
                        .withReference(mr.getReference().withPrefix(mr.getContaining().getFormatting().getSuffix()))));
            }
        }

        return changes;
    }

    @Override
    public List<AstTransform> visitAssign(Tr.Assign assign) {
        List<AstTransform> changes = super.visitAssign(assign);

        if (tokens.contains(ASSIGN)) {
            if (option == WrapPolicy.NL) {
                if (assign.getAssignment().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(assign, a -> a
                            .withVariable(a.getVariable().withSuffix(a.getAssignment().getFormatting().getPrefix()))
                            .withAssignment(a.getAssignment().withPrefix(" "))));
                }
            } else if (assign.getVariable().getFormatting().getSuffix().contains("\n")) {
                changes.addAll(transform(assign, a -> a
                        .withVariable(a.getVariable().withSuffix(" "))
                        .withAssignment(a.getAssignment().withPrefix(a.getVariable().getFormatting().getSuffix()))));
            }
        }

        return changes;
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    public List<AstTransform> visitVariable(Tr.VariableDecls.NamedVar variable) {
        List<AstTransform> changes = super.visitVariable(variable);

        if (tokens.contains(ASSIGN) && variable.getInitializer() != null) {
            if (option == WrapPolicy.NL) {
                if (variable.getInitializer().getFormatting().getPrefix().contains("\n")) {
                    changes.addAll(transform(variable, v -> v
                            .withDimensionsAfterName(formatLastSuffix(v.getDimensionsAfterName(), v.getInitializer().getFormatting().getPrefix()))
                            .withName(v.getDimensionsAfterName().isEmpty() ? v.getName().withSuffix(v.getInitializer().getFormatting().getPrefix()) :
                                    v.getName())
                            .withInitializer(v.getInitializer().withPrefix(" "))));
                }
            } else if (lastTreeBeforeInitializer(variable).getFormatting().getSuffix().contains("\n")) {
                changes.addAll(transform(variable, v -> v
                        .withDimensionsAfterName(formatLastSuffix(v.getDimensionsAfterName(), " "))
                        .withName(v.getDimensionsAfterName().isEmpty() ? v.getName().withSuffix(" ") : v.getName())
                        .withInitializer(v.getInitializer().withPrefix(lastTreeBeforeInitializer(v).getFormatting().getSuffix()))));
            }
        }

        return changes;
    }

    private Tree lastTreeBeforeInitializer(Tr.VariableDecls.NamedVar var) {
        if (!var.getDimensionsAfterName().isEmpty()) {
            return var.getDimensionsAfterName().get(var.getDimensionsAfterName().size() - 1);
        }
        return var.getName();
    }
}
