package org.gradle.rewrite.checkstyle;

import org.apache.commons.cli.*;
import org.openrewrite.Change;
import org.openrewrite.Refactor;
import org.openrewrite.SourceVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class Main {
    public static void main(String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        options.addRequiredOption("f", "file", true, "Checkstyle configuration XML");
        options.addOption("l", "limit", true, "Limit number of files processed");
        options.addOption("r", "regex", true, "Glob filter");

        CommandLine line = parser.parse(options, args);

        try (FileInputStream fis = new FileInputStream(new File(line.getOptionValue("f")))) {
            List<SourceVisitor<J>> rules = new RewriteCheckstyle(fis).getVisitors();

            PathMatcher pathMatcher = line.hasOption("r") ?
                    FileSystems.getDefault().getPathMatcher("glob:" + line.getOptionValue("r")) :
                    null;

            List<Path> sourcePaths = Files.walk(Path.of(""))
                    .filter(p -> p.toFile().getName().endsWith(".java"))
                    .filter(p -> pathMatcher == null || pathMatcher.matches(p))
                    .limit(Integer.parseInt(line.getOptionValue("l", "2147483647")))
                    .collect(toList());

            sourcePaths.stream()
                .flatMap(javaSource ->
                        new JavaParser().setLogCompilationWarningsAndErrors(false).parse(singletonList(javaSource), Path.of("").toAbsolutePath())
                            .stream())
                .forEach(cu -> {
                    Refactor<J.CompilationUnit, J> refactor = cu.refactor();
                    for (SourceVisitor<J> visitor : rules) {
                        refactor.visit(visitor);
                    }

                    Change<J.CompilationUnit> fixed = refactor.fix();
                    if (!fixed.getGetRulesThatMadeChanges().isEmpty()) {
                        System.out.println(cu.getSourcePath() + " changed by: ");
                        fixed.getGetRulesThatMadeChanges().forEach(rule -> System.out.println("  " + rule));
                        try {
                            Files.writeString(new File(cu.getSourcePath()).toPath(), fixed.getFixed().print());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
