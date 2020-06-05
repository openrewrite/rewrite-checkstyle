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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Charsets;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micrometer.prometheus.rsocket.PrometheusRSocketClient;
import io.rsocket.transport.netty.client.TcpClientTransport;
import org.openrewrite.Change;
import org.openrewrite.Refactor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.tree.J;
import org.slf4j.LoggerFactory;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;

public class Main {
    static {
        Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logger.setLevel(Level.INFO);
    }

    public static void main(String[] args) throws ParseException, IOException {
        PrometheusRSocketClient metricsClient = null;

        try {
            CommandLineParser parser = new DefaultParser();
            Options options = new Options();
            options.addOption("f", "file", true, "Checkstyle configuration XML file");
            options.addOption("c", "config", true, "Checkstyle configuration XML");
            options.addOption("l", "limit", true, "Limit number of files processed");
            options.addOption("r", "regex", true, "Glob filter");
            options.addOption("m", "metrics", false, "Publish metrics");

            CommandLine line = parser.parse(options, args);

            if (line.hasOption("m")) {
                PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
                metricsClient = PrometheusRSocketClient
                        .build(prometheusMeterRegistry, TcpClientTransport.create(TcpClient.create().host("localhost").port(7001)))
                        .retry(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(10)).maxBackoff(Duration.ofMinutes(10)))
                        .connect();

                Metrics.globalRegistry.add(prometheusMeterRegistry);

                new JvmGcMetrics().bindTo(Metrics.globalRegistry);
                new ProcessorMetrics().bindTo(Metrics.globalRegistry);
            }

            RewriteCheckstyle rewriteCheckstyle;

            if (line.hasOption("f")) {
                try (InputStream is = new FileInputStream(new File(line.getOptionValue("f")))) {
                    rewriteCheckstyle = new RewriteCheckstyle(is);
                }
            } else if (line.hasOption("c")) {
                try (InputStream is = new ByteArrayInputStream(line.getOptionValue("c").getBytes(Charsets.UTF_8))) {
                    rewriteCheckstyle = new RewriteCheckstyle(is);
                }
            } else {
                throw new IllegalArgumentException("Supply either a config XML file via -f or an inline config via -c");
            }

            PathMatcher pathMatcher = line.hasOption("r") ?
                    FileSystems.getDefault().getPathMatcher("glob:" + line.getOptionValue("r")) :
                    null;

            List<Path> sourcePaths = Files.walk(Path.of(""))
                    .filter(p -> p.toFile().getName().endsWith(".java"))
                    .filter(p -> pathMatcher == null || pathMatcher.matches(p))
                    .limit(Integer.parseInt(line.getOptionValue("l", "2147483647")))
                    .collect(toList());

            sourcePaths.stream()
                    .flatMap(javaSource -> {
                        try {
                            return new JavaParser()
                                    .setLogCompilationWarningsAndErrors(false)
                                    .parse(singletonList(javaSource), Path.of("").toAbsolutePath())
                                    .stream();
                        } catch (Throwable t) {
                            try {
                                Files.writeString(Path.of("errors-parsing.log"), javaSource.toString(), StandardOpenOption.APPEND);
                            } catch (IOException ignored) {
                            }
                            return Stream.empty();
                        }
                    })
                    .forEach(cu -> {
                        Refactor<J.CompilationUnit, J> refactor = rewriteCheckstyle.apply(cu.refactor());

                        Change<J.CompilationUnit> fixed = refactor.fix();
                        if (!fixed.getRulesThatMadeChanges().isEmpty()) {
                            fixed.getRulesThatMadeChanges().forEach(rule -> System.out.println("  " + rule));
                            try {
                                Files.writeString(new File(cu.getSourcePath()).toPath(), fixed.getFixed().print());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                    });
        } finally {
            if (metricsClient != null) {
                metricsClient.pushAndClose().block(Duration.ofSeconds(10));
            }
        }
    }
}
