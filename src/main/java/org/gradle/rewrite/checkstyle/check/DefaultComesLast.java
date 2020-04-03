package org.gradle.rewrite.checkstyle.check;

import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static org.openrewrite.Tree.randomId;

public class DefaultComesLast extends JavaRefactorVisitor {
    private final boolean skipIfLastAndSharedWithCase;

    public DefaultComesLast(boolean skipIfLastAndSharedWithCase) {
        this.skipIfLastAndSharedWithCase = skipIfLastAndSharedWithCase;
    }

    public DefaultComesLast() {
        this(false);
    }

    @Override
    public String getName() {
        return "checkstyle.DefaultComesLast";
    }

    @Override
    public J visitSwitch(J.Switch switzh) {
        J.Switch s = refactor(switzh, super::visitSwitch);

        if (!defaultIsLastOrNotPresent(switzh)) {
            List<J.Case> cases = s.getCases().getStatements();
            List<J.Case> fixedCases = new ArrayList<>(cases.size());

            int defaultCaseIndex = -1;
            J.Case defaultCase = null;

            for (int i = 0; i < cases.size(); i++) {
                J.Case aCase = cases.get(i);
                if (isDefault(aCase)) {
                    defaultCaseIndex = i;
                    defaultCase = aCase;
                }
            }

            List<J.Case> casesGroupedWithDefault = new ArrayList<>();

            boolean foundNonEmptyCase = false;
            for (int i = defaultCaseIndex - 1; i >= 0; i--) {
                J.Case aCase = cases.get(i);
                if (aCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                    casesGroupedWithDefault.add(0, aCase);
                } else {
                    foundNonEmptyCase = true;
                    fixedCases.add(0, aCase);
                }
            }

            foundNonEmptyCase = false;
            for (int i = defaultCaseIndex + 1; i < cases.size(); i++) {
                J.Case aCase = cases.get(i);
                if (defaultCase != null && defaultCase.getStatements().isEmpty() &&
                        aCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                    casesGroupedWithDefault.add(aCase);
                } else {
                    if (defaultCase != null && defaultCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                        // the last case grouped with default can be non-empty. it will be flipped with
                        // the default case, including its statements
                        casesGroupedWithDefault.add(aCase);
                    }
                    foundNonEmptyCase = true;
                    fixedCases.add(aCase);
                }
            }

            if (defaultCase != null && !casesGroupedWithDefault.isEmpty()) {
                J.Case lastGroupedWithDefault = casesGroupedWithDefault.get(casesGroupedWithDefault.size() - 1);
                if (!lastGroupedWithDefault.getStatements().isEmpty()) {
                    casesGroupedWithDefault.set(casesGroupedWithDefault.size() - 1,
                            lastGroupedWithDefault.withStatements(emptyList()));
                    defaultCase = defaultCase.withStatements(lastGroupedWithDefault.getStatements());
                }
            }

            J.Case lastNotGroupedWithDefault = fixedCases.get(fixedCases.size() - 1);
            if (!lastNotGroupedWithDefault.getStatements().stream().reduce((s1, s2) -> s2)
                    .map(stat -> stat instanceof J.Break || stat instanceof J.Continue ||
                            stat instanceof J.Return || stat instanceof J.Throw)
                    .orElse(false)) {

                // add a break statement since this case is now no longer last and would fall through
                List<Statement> stats = new ArrayList<>(lastNotGroupedWithDefault.getStatements());
                stats.add(new J.Break(randomId(), null, formatter.format(lastNotGroupedWithDefault)));

                fixedCases.set(fixedCases.size() - 1, lastNotGroupedWithDefault.withStatements(stats));
            }

            fixedCases.addAll(casesGroupedWithDefault);

            if (defaultCase != null) {
                if (defaultCase.getStatements().stream().reduce((s1, s2) -> s2)
                        .map(stat -> stat instanceof J.Break || stat instanceof J.Continue ||
                                stat instanceof J.Return)
                        .orElse(false)) {
                    List<Statement> fixedDefaultStatements = new ArrayList<>(defaultCase.getStatements());
                    fixedDefaultStatements.remove(fixedDefaultStatements.size() - 1);
                    fixedCases.add(defaultCase.withStatements(fixedDefaultStatements));
                } else {
                    fixedCases.add(defaultCase);
                }
            }

            s = s.withCases(s.getCases().withStatements(fixedCases));
        }

        return s;
    }

    private boolean defaultIsLastOrNotPresent(J.Switch switzh) {
        J.Case defaultCase = null;
        J.Case prior = null;
        for (J.Case aCase : switzh.getCases().getStatements()) {
            if (isDefault(aCase)) {
                defaultCase = aCase;
            }

            if (defaultCase != null && prior != null) {
                return skipIfLastAndSharedWithCase && prior.getStatements().isEmpty();
            }

            prior = aCase;
        }

        // either default was not present or it was last
        return true;
    }

    private boolean isDefault(J.Case caze) {
        return caze.getPattern() != null && caze.getPattern().printTrimmed().equals("default");
    }
}
