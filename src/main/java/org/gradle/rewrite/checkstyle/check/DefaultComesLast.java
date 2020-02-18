package org.gradle.rewrite.checkstyle.check;

import com.netflix.rewrite.tree.Statement;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.visitor.refactor.AstTransform;
import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;

import java.util.ArrayList;
import java.util.List;

import static com.netflix.rewrite.tree.Tr.randomId;
import static java.util.Collections.emptyList;

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

                    int defaultCaseIndex = -1;
                    Tr.Case defaultCase = null;

                    for (int i = 0; i < cases.size(); i++) {
                        Tr.Case aCase = cases.get(i);
                        if (isDefault(aCase)) {
                            defaultCaseIndex = i;
                            defaultCase = aCase;
                        }
                    }

                    List<Tr.Case> casesGroupedWithDefault = new ArrayList<>();

                    boolean foundNonEmptyCase = false;
                    for(int i = defaultCaseIndex - 1; i >= 0; i--) {
                        Tr.Case aCase = cases.get(i);
                        if(aCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                            casesGroupedWithDefault.add(0, aCase);
                        }
                        else {
                            foundNonEmptyCase = true;
                            fixedCases.add(0, aCase);
                        }
                    }

                    foundNonEmptyCase = false;
                    for(int i = defaultCaseIndex + 1; i < cases.size(); i++) {
                        Tr.Case aCase = cases.get(i);
                        if(defaultCase != null && defaultCase.getStatements().isEmpty() &&
                                aCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                            casesGroupedWithDefault.add(aCase);
                        }
                        else {
                            if(defaultCase != null && defaultCase.getStatements().isEmpty() && !foundNonEmptyCase) {
                                // the last case grouped with default can be non-empty. it will be flipped with
                                // the default case, including its statements
                                casesGroupedWithDefault.add(aCase);
                            }
                            foundNonEmptyCase = true;
                            fixedCases.add(aCase);
                        }
                    }

                    if(defaultCase != null && !casesGroupedWithDefault.isEmpty()) {
                        Tr.Case lastGroupedWithDefault = casesGroupedWithDefault.get(casesGroupedWithDefault.size() - 1);
                        if(!lastGroupedWithDefault.getStatements().isEmpty()) {
                            casesGroupedWithDefault.set(casesGroupedWithDefault.size() - 1,
                                    lastGroupedWithDefault.withStatements(emptyList()));
                            defaultCase = defaultCase.withStatements(lastGroupedWithDefault.getStatements());
                        }
                    }

                    Tr.Case lastNotGroupedWithDefault = fixedCases.get(fixedCases.size() - 1);
                    if (!lastNotGroupedWithDefault.getStatements().stream().reduce((s1, s2) -> s2)
                            .map(stat -> stat instanceof Tr.Break || stat instanceof Tr.Continue ||
                                    stat instanceof Tr.Return || stat instanceof Tr.Throw)
                            .orElse(false)) {

                        // add a break statement since this case is now no longer last and would fall through
                        List<Statement> stats = new ArrayList<>(lastNotGroupedWithDefault.getStatements());
                        stats.add(new Tr.Break(randomId(), null, formatter().format(lastNotGroupedWithDefault)));

                        fixedCases.set(fixedCases.size() - 1, lastNotGroupedWithDefault.withStatements(stats));
                    }

                    fixedCases.addAll(casesGroupedWithDefault);

                    if(defaultCase != null) {
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

    private boolean isDefault(Tr.Case caze) {
        return caze.getPattern() != null && caze.getPattern().printTrimmed().equals("default");
    }
}
