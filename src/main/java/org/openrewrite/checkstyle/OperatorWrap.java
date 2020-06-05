/*
 * Copyright 2020 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.checkstyle;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.openrewrite.Tree;
import org.openrewrite.checkstyle.policy.OperatorToken;
import org.openrewrite.checkstyle.policy.WrapPolicy;
import org.openrewrite.config.AutoConfigure;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.openrewrite.Formatting.*;
import static org.openrewrite.checkstyle.policy.OperatorToken.*;

@AutoConfigure
public class OperatorWrap extends CheckstyleRefactorVisitor {
    private static final Set<OperatorToken> DEFAULT_TOKENS = Set.of(
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

    private WrapPolicy option;
    private Set<OperatorToken> tokens;

    @Override
    protected void configure(Module m) {
        this.option = m.propAsOptionValue(WrapPolicy::valueOf, WrapPolicy.NL);
        this.tokens = m.propAsTokens(OperatorToken.class, DEFAULT_TOKENS);
    }

    @Override
    public Iterable<Tag> getTags() {
        return Tags.of("policy", option.toString());
    }

    @Override
    public J visitBinary(J.Binary binary) {
        J.Binary b = refactor(binary, super::visitBinary);
        J.Binary.Operator op = binary.getOperator();

        if ((tokens.contains(DIV) && op instanceof J.Binary.Operator.Division) ||
                (tokens.contains(STAR) && op instanceof J.Binary.Operator.Multiplication) ||
                (tokens.contains(PLUS) && op instanceof J.Binary.Operator.Addition) ||
                (tokens.contains(MINUS) && op instanceof J.Binary.Operator.Subtraction) ||
                (tokens.contains(MOD) && op instanceof J.Binary.Operator.Modulo) ||
                (tokens.contains(SR) && op instanceof J.Binary.Operator.RightShift) ||
                (tokens.contains(SL) && op instanceof J.Binary.Operator.LeftShift) ||
                (tokens.contains(BSR) && op instanceof J.Binary.Operator.UnsignedRightShift) ||
                (tokens.contains(EQUAL) && op instanceof J.Binary.Operator.Equal) ||
                (tokens.contains(NOT_EQUAL) && op instanceof J.Binary.Operator.NotEqual) ||
                (tokens.contains(GT) && op instanceof J.Binary.Operator.GreaterThan) ||
                (tokens.contains(GE) && op instanceof J.Binary.Operator.GreaterThanOrEqual) ||
                (tokens.contains(LT) && op instanceof J.Binary.Operator.LessThan) ||
                (tokens.contains(LE) && op instanceof J.Binary.Operator.LessThanOrEqual) ||
                (tokens.contains(BAND) && op instanceof J.Binary.Operator.BitAnd) ||
                (tokens.contains(BXOR) && op instanceof J.Binary.Operator.BitXor) ||
                (tokens.contains(BOR) && op instanceof J.Binary.Operator.BitOr) ||
                (tokens.contains(LAND) && op instanceof J.Binary.Operator.And) ||
                (tokens.contains(LOR) && op instanceof J.Binary.Operator.Or)) {

            if (option == WrapPolicy.NL) {
                if (binary.getRight().getFormatting().getPrefix().contains("\n")) {
                    b = b.withOperator(b.getOperator().withPrefix(b.getRight().getFormatting().getPrefix()))
                            .withRight(b.getRight().withPrefix(" "));
                }
            } else if (op.getFormatting().getPrefix().contains("\n")) {
                b = b
                        .withOperator(b.getOperator().withPrefix(" "))
                        .withRight(b.getRight().withPrefix(op.getFormatting().getPrefix()));
            }
        }

        return b;
    }

    @Override
    public J visitTypeParameter(J.TypeParameter typeParam) {
        J.TypeParameter t = refactor(typeParam, super::visitTypeParameter);

        if (tokens.contains(TYPE_EXTENSION_AND) && t.getBounds() != null) {
            List<TypeTree> types = new ArrayList<>(t.getBounds().getTypes());
            boolean changed = false;

            for (int i = 0; i < types.size() - 1; i++) {
                TypeTree tp1 = types.get(i);
                TypeTree tp2 = types.get(i + 1);

                if (option == WrapPolicy.NL) {
                    if (tp2.getFormatting().getPrefix().contains("\n")) {
                        tp1 = tp1.withSuffix(tp2.getFormatting().getPrefix());
                        tp2 = tp2.withPrefix(" ");
                        changed = true;
                        types.set(i, tp1);
                        types.set(i + 1, tp2);
                    }
                } else if (tp1.getFormatting().getSuffix().contains("\n")) {
                    tp2 = tp2.withPrefix(tp1.getFormatting().getSuffix());
                    tp1 = tp1.withSuffix(" ");
                    changed = true;
                    types.set(i, tp1);
                    types.set(i + 1, tp2);
                }
            }

            if (changed) {
                t = t.withBounds(t.getBounds().withTypes(types));
            }
        }

        return t;
    }

    @Override
    public J visitInstanceOf(J.InstanceOf instanceOf) {
        J.InstanceOf i = refactor(instanceOf, super::visitInstanceOf);

        if (tokens.contains(LITERAL_INSTANCEOF)) {
            if (option == WrapPolicy.NL) {
                if (instanceOf.getClazz().getFormatting().getPrefix().contains("\n")) {
                    i = i.withExpr(i.getExpr().withSuffix(i.getClazz().getFormatting().getPrefix()))
                            .withClazz(i.getClazz().withPrefix(" "));
                }
            } else if (instanceOf.getExpr().getFormatting().getSuffix().contains("\n")) {
                i = i.withExpr(i.getExpr().withSuffix(" "))
                        .withClazz(i.getClazz().withPrefix(i.getExpr().getFormatting().getSuffix()));
            }
        }

        return i;
    }

    @Override
    public J visitTernary(J.Ternary ternary) {
        J.Ternary t = refactor(ternary, super::visitTernary);

        if (tokens.contains(QUESTION)) {
            if (option == WrapPolicy.NL) {
                if (ternary.getTruePart().getFormatting().getPrefix().contains("\n")) {
                    t = t.withCondition(t.getCondition().withSuffix(t.getTruePart().getFormatting().getPrefix()))
                            .withTruePart(t.getTruePart().withPrefix(" "));
                }
            } else if (ternary.getCondition().getFormatting().getSuffix().contains("\n")) {
                t = t.withCondition(t.getCondition().withSuffix(" "))
                        .withTruePart(t.getTruePart().withPrefix(t.getCondition().getFormatting().getSuffix()));
            }
        }

        if (tokens.contains(COLON)) {
            if (option == WrapPolicy.NL) {
                if (ternary.getFalsePart().getFormatting().getPrefix().contains("\n")) {
                    t = t.withTruePart(t.getTruePart().withSuffix(t.getFalsePart().getFormatting().getPrefix()))
                            .withFalsePart(t.getFalsePart().withPrefix(" "));
                }
            } else if (ternary.getTruePart().getFormatting().getSuffix().contains("\n")) {
                t = t.withTruePart(t.getTruePart().withSuffix(" "))
                        .withFalsePart(t.getFalsePart().withPrefix(t.getTruePart().getFormatting().getSuffix()));
            }
        }

        return t;
    }

    @Override
    public J visitAssignOp(J.AssignOp assignOp) {
        J.AssignOp a = refactor(assignOp, super::visitAssignOp);
        J.AssignOp.Operator op = assignOp.getOperator();

        if ((tokens.contains(PLUS_ASSIGN) && op instanceof J.AssignOp.Operator.Addition) ||
                (tokens.contains(MINUS_ASSIGN) && op instanceof J.AssignOp.Operator.Subtraction) ||
                (tokens.contains(STAR_ASSIGN) && op instanceof J.AssignOp.Operator.Multiplication) ||
                (tokens.contains(DIV_ASSIGN) && op instanceof J.AssignOp.Operator.Division) ||
                (tokens.contains(MOD_ASSIGN) && op instanceof J.AssignOp.Operator.Modulo) ||
                (tokens.contains(SR_ASSIGN) && op instanceof J.AssignOp.Operator.RightShift) ||
                (tokens.contains(SL_ASSIGN) && op instanceof J.AssignOp.Operator.LeftShift) ||
                (tokens.contains(BSR_ASSIGN) && op instanceof J.AssignOp.Operator.UnsignedRightShift) ||
                (tokens.contains(BAND_ASSIGN) && op instanceof J.AssignOp.Operator.BitAnd) ||
                (tokens.contains(BXOR_ASSIGN) && op instanceof J.AssignOp.Operator.BitXor) ||
                (tokens.contains(BOR_ASSIGN) && op instanceof J.AssignOp.Operator.BitOr)) {

            if (option == WrapPolicy.NL) {
                if (assignOp.getAssignment().getFormatting().getPrefix().contains("\n")) {
                    a = a.withOperator(a.getOperator().withPrefix(a.getAssignment().getFormatting().getPrefix()))
                            .withAssignment(a.getAssignment().withPrefix(" "));
                }
            } else if (op.getFormatting().getPrefix().contains("\n")) {
                a = a.withOperator(a.getOperator().withPrefix(" "))
                        .withAssignment(a.getAssignment().withPrefix(op.getFormatting().getPrefix()));
            }
        }

        return a;
    }

    @Override
    public J visitMemberReference(J.MemberReference memberRef) {
        J.MemberReference m = refactor(memberRef, super::visitMemberReference);

        if (tokens.contains(METHOD_REF)) {
            if (option == WrapPolicy.NL) {
                if (memberRef.getReference().getFormatting().getPrefix().contains("\n")) {
                    m = m.withContaining(m.getContaining().withSuffix(m.getReference().getFormatting().getPrefix()))
                            .withReference(stripPrefix(m.getReference()));
                }
            } else if (memberRef.getContaining().getFormatting().getSuffix().contains("\n")) {
                m = m.withContaining(stripSuffix(m.getContaining()))
                        .withReference(m.getReference().withPrefix(m.getContaining().getFormatting().getSuffix()));
            }
        }

        return m;
    }

    @Override
    public J visitAssign(J.Assign assign) {
        J.Assign a = refactor(assign, super::visitAssign);

        if (tokens.contains(ASSIGN)) {
            if (option == WrapPolicy.NL) {
                if (assign.getAssignment().getFormatting().getPrefix().contains("\n")) {
                    a = a.withVariable(a.getVariable().withSuffix(a.getAssignment().getFormatting().getPrefix()))
                            .withAssignment(a.getAssignment().withPrefix(" "));
                }
            } else if (assign.getVariable().getFormatting().getSuffix().contains("\n")) {
                a = a.withVariable(a.getVariable().withSuffix(" "))
                        .withAssignment(a.getAssignment().withPrefix(a.getVariable().getFormatting().getSuffix()));
            }
        }

        return a;
    }

    @Override
    public J visitVariable(J.VariableDecls.NamedVar variable) {
        J.VariableDecls.NamedVar v = refactor(variable, super::visitVariable);

        if (tokens.contains(ASSIGN) && variable.getInitializer() != null) {
            if (option == WrapPolicy.NL) {
                if (variable.getInitializer().getFormatting().getPrefix().contains("\n")) {
                    v = v.withDimensionsAfterName(formatLastSuffix(v.getDimensionsAfterName(), v.getInitializer().getFormatting().getPrefix()))
                            .withName(v.getDimensionsAfterName().isEmpty() ? v.getName().withSuffix(v.getInitializer().getFormatting().getPrefix()) :
                                    v.getName())
                            .withInitializer(v.getInitializer().withPrefix(" "));
                }
            } else if (lastTreeBeforeInitializer(variable).getFormatting().getSuffix().contains("\n")) {
                v = v.withDimensionsAfterName(formatLastSuffix(v.getDimensionsAfterName(), " "))
                        .withName(v.getDimensionsAfterName().isEmpty() ? v.getName().withSuffix(" ") : v.getName())
                        .withInitializer(v.getInitializer().withPrefix(lastTreeBeforeInitializer(v).getFormatting().getSuffix()));
            }
        }

        return v;
    }

    private Tree lastTreeBeforeInitializer(J.VariableDecls.NamedVar var) {
        if (!var.getDimensionsAfterName().isEmpty()) {
            return var.getDimensionsAfterName().get(var.getDimensionsAfterName().size() - 1);
        }
        return var.getName();
    }
}
