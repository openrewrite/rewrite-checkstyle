package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;

import java.util.ArrayList;
import java.util.List;

import static com.netflix.rewrite.tree.Tr.randomId;

public class DefaultComesLast extends RefactorVisitor {
    private final boolean skipIfLastAndSharedWithCase;

    public DefaultComesLast(boolean skipIfLastAndSharedWithCase) {
        this.skipIfLastAndSharedWithCase = skipIfLastAndSharedWithCase;
    }

    public DefaultComesLast() {
        this(false);
    }

    @Override
    public String getRuleName() {
        return "DefaultComesLast";
    }

    @Override
    public List<AstTransform> visitSwitch(Tr.Switch switzh) {
        return maybeTransform(
                switzh,
                !defaultIsLastOrNotPresent(switzh),
                super::visitSwitch,
                s -> {
                    List<Tr.Case> cases = s.getCases().getStatements();
                    List<Tr.Case> fixedCases = new ArrayList<>(cases.size());

                    Tr.Case defaultCase = null;
                    for (Tr.Case aCase : cases) {
                        if (isDefault(aCase)) {
                            defaultCase = aCase;
                        } else {
                            fixedCases.add(aCase);
                        }
                    }

                    Tr.Case penultimate = cases.get(cases.size() - 1);
                    if (!penultimate.getStatements().stream().reduce((s1, s2) -> s2)
                            .map(stat -> stat instanceof Tr.Break || stat instanceof Tr.Continue ||
                                    stat instanceof Tr.Return || stat instanceof Tr.Throw)
                            .orElse(false)) {

                        // add a break statement since this case is now no longer last and would fall through
                        List<Statement> stats = new ArrayList<>(penultimate.getStatements());
                        stats.add(new Tr.Break(randomId(), null, formatter().format(penultimate)));

                        penultimate = penultimate.withStatements(stats);
                    }

                    fixedCases.add(penultimate.withStatements(penultimate.getStatements()));

                    // should never be null
                    if (defaultCase != null) {
                        if (defaultCase.getStatements().stream().reduce((s1, s2) -> s2)
                                .map(stat -> stat instanceof Tr.Break || stat instanceof Tr.Continue ||
                                        stat instanceof Tr.Return || stat instanceof Tr.Throw)
                                .orElse(false)) {
                            List<Statement> fixedDefaultStatements = new ArrayList<>(defaultCase.getStatements());
                            fixedDefaultStatements.remove(fixedDefaultStatements.size() - 1);
                            fixedCases.add(defaultCase.withStatements(fixedDefaultStatements));
                        } else {
                            fixedCases.add(defaultCase);
                        }
                    }

                    return s.withCases(s.getCases().withStatements(fixedCases));
                }
        );
    }

    private boolean defaultIsLastOrNotPresent(Tr.Switch switzh) {
        Tr.Case defaultCase = null;
        Tr.Case prior = null;
        for (Tr.Case aCase : switzh.getCases().getStatements()) {
            if (defaultCase != null) {
                return skipIfLastAndSharedWithCase && prior.getStatements().isEmpty();
            }

            if (isDefault(aCase)) {
                defaultCase = aCase;
            }

            prior = aCase;
        }

        // either default was not present or it was last
        return true;
    }

    private boolean isDefault(Tr.Case caze) {
        return caze.getPattern() != null && caze.getPattern().printTrimmed().equals("default");
    }
}
