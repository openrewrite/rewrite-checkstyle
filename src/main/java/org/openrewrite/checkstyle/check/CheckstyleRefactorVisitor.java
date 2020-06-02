/*
 * Copyright ${year} the original author or authors.
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
package org.openrewrite.checkstyle.check;

import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.api.*;
import com.puppycrawl.tools.checkstyle.filters.SuppressionsLoader;
import org.eclipse.microprofile.config.Config;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.refactor.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.xml.sax.InputSource;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

public abstract class CheckstyleRefactorVisitor extends JavaRefactorVisitor {
    // we just want to re-use the suppression filtering logic in Checkstyle without emitting messages
    private static final LocalizedMessage localizedMessageThatDoesntMatter = new LocalizedMessage(1,
            "bundle", "key", new String[0], null, CheckstyleRefactorVisitor.class, null);

    private static final Map<File, LoadedConfiguration> loadedConfigurationsByPath = new HashMap<>();

    FilterSet suppressions = new FilterSet();

    public CheckstyleRefactorVisitor(String name, String... tagKeyValues) {
        super(name, tagKeyValues);
    }

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        if (suppressions.accept(new AuditEvent("does not matter", cu.getSourcePath(), localizedMessageThatDoesntMatter))) {
            return super.visitCompilationUnit(cu);
        }
        return cu;
    }

    @Nullable
    protected static <V extends CheckstyleRefactorVisitor> V fromModule(Config config, String name, Function<Module, V> buildVisitor) {
        try {
            LoadedConfiguration loadedConfiguration;
            Optional<File> file = config.getOptionalValue("checkstyle.configFile", File.class);
            if (file.isPresent()) {
                loadedConfiguration = loadedConfigurationsByPath.get(file.get());
                if(loadedConfiguration == null) {
                    try (InputStream inputStream = new FileInputStream(file.get())) {
                        loadedConfiguration = loadConfiguration(inputStream, config);
                        loadedConfigurationsByPath.put(file.get(), loadedConfiguration);
                    }
                }
            } else {
                try (InputStream inputStream = new ByteArrayInputStream(config.getValue("checkstyle.config", String.class)
                        .getBytes(Charset.defaultCharset()))) {
                    loadedConfiguration = loadConfiguration(inputStream, config);
                }
            }

            Module module = loadedConfiguration.modulesByName.get(name);
            if(module == null) {
                return null;
            }

            V visitor = buildVisitor.apply(module);
            visitor.suppressions = loadedConfiguration.suppressions;
            return visitor;
        } catch (IOException | CheckstyleException e) {
            throw new RuntimeException(e);
        }
    }

    private static LoadedConfiguration loadConfiguration(InputStream inputStream, Config config) throws CheckstyleException {
        Configuration checkstyleConfig = ConfigurationLoader.loadConfiguration(new InputSource(inputStream),
                name -> {
                    Object prop = config.getValue(name, Object.class);
                    return prop == null ?
                            name.equals("config_loc") ? "config/checkstyle" : null :
                            prop.toString();
                },
                ConfigurationLoader.IgnoredModulesOptions.OMIT);

        FilterSet suppressions = new FilterSet();
        for (Configuration firstLevelChild : checkstyleConfig.getChildren()) {
            if ("SuppressionFilter".equals(firstLevelChild.getName())) {
                for (String attributeName : firstLevelChild.getAttributeNames()) {
                    if ("file".equals(attributeName)) {
                        suppressions = SuppressionsLoader.loadSuppressions(firstLevelChild.getAttribute("file"));
                    }
                }
            }
        }

        Collection<Module> modules = new ArrayList<>();
        for (Configuration firstLevelChild : checkstyleConfig.getChildren()) {
            if ("TreeWalker".equals(firstLevelChild.getName())) {
                modules.addAll(
                        stream(firstLevelChild.getChildren())
                                .map(child -> {
                                    try {
                                        Map<String, String> properties = new HashMap<>();
                                        for (String propertyName : child.getAttributeNames()) {
                                            properties.put(propertyName, child.getAttribute(propertyName));
                                        }
                                        return new Module(child.getName(), properties);
                                    } catch (CheckstyleException e) {
                                        return null;
                                    }
                                })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList())
                );
            }
        }

        return new LoadedConfiguration(modules, suppressions);
    }

    private static class LoadedConfiguration {
        private final Map<String, Module> modulesByName;
        private final FilterSet suppressions;

        public LoadedConfiguration(Collection<Module> modules, FilterSet suppressions) {
            this.modulesByName = modules.stream().collect(toMap(Module::getName, identity()));
            this.suppressions = suppressions;
        }
    }

    protected static class Module {
        private final String name;
        private final Map<String, String> properties;

        public Module(String name, Map<String, String> properties) {
            this.name = name;
            this.properties = properties;
        }

        public String getName() {
            return name;
        }

        public boolean prop(String key, boolean defaultValue) {
            return properties.containsKey(key) ? Boolean.parseBoolean(properties.get(key))
                    : defaultValue;
        }

        public Pattern prop(String key, Pattern defaultValue) {
            if (properties.containsKey(key)) {
                try {
                    return Pattern.compile(properties.get(key));
                } catch (Throwable ignored) {
                }
            }
            return defaultValue;
        }

        public <T extends Enum<T>> Set<T> propAsTokens(Class<T> enumType, Set<T> defaultValue) {
            return properties.containsKey("tokens") ?
                    stream(properties.get("tokens").split("\\s*,\\s*"))
                            .map(token -> {
                                try {
                                    return Enum.valueOf(enumType, token);
                                } catch (Throwable t) {
                                    return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(toSet()) :
                    defaultValue;
        }

        public <T> T propAsOptionValue(Function<String, T> valueOf, T defaultValue) {
            try {
                return valueOf.apply(toUpper(properties.get("option")));
            } catch (Throwable t) {
                try {
                    return valueOf.apply(properties.get("option").toUpperCase());
                } catch (Throwable t2) {
                    return defaultValue;
                }
            }
        }

        @Nullable
        private String toUpper(String value) {
            return (value != null && value.length() > 1) ? value.substring(0, 1).toUpperCase() + value.substring(1) : null;
        }
    }
}
