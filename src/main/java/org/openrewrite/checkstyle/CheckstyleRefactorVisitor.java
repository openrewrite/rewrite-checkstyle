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

import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.api.*;
import com.puppycrawl.tools.checkstyle.filters.SuppressionsLoader;
import org.openrewrite.Validated;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaRefactorVisitor;
import org.openrewrite.java.tree.J;
import org.xml.sax.InputSource;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Used, especially by build tools, to relativize {@link #configFile} against some
     * root directory. This is optional, and {@link #configFile} can itself be an absolute
     * or resolvable relative path independent of this.
     */
    @Nullable
    private Path baseDir;

    private File configFile;
    private String config;
    private Map<String, Object> properties;
    private FilterSet suppressions = new FilterSet();

    @Override
    public J visitCompilationUnit(J.CompilationUnit cu) {
        if (suppressions.accept(new AuditEvent("does not matter", cu.getSourcePath().toString(), localizedMessageThatDoesntMatter))) {
            return super.visitCompilationUnit(cu);
        }
        return cu;
    }

    public void setConfig(String config) {
        this.config = config;
        validate();
    }

    public void setBaseDir(@Nullable Path baseDir) {
        this.baseDir = baseDir;
    }

    public void setConfigFile(File configFile) {
        this.configFile = configFile;
        validate();
    }

    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
        validate();
    }

    protected void configure(Module m) {
    }

    @Override
    public final Validated validate() {
        try {
            LoadedConfiguration loadedConfiguration = null;
            if (configFile != null) {
                File absoluteConfigFile = baseDir == null ? configFile :
                        baseDir.resolve(configFile.toPath()).toFile();

                if(absoluteConfigFile.exists()) {
                    loadedConfiguration = loadedConfigurationsByPath.get(absoluteConfigFile);
                    if (loadedConfiguration == null) {
                        try (InputStream inputStream = new FileInputStream(absoluteConfigFile)) {
                            loadedConfiguration = loadConfiguration(inputStream);
                            loadedConfigurationsByPath.put(absoluteConfigFile, loadedConfiguration);
                        }
                    }
                }
            }

            if(loadedConfiguration == null) {
                if (config != null) {
                    try (InputStream inputStream = new ByteArrayInputStream(config.getBytes(Charset.defaultCharset()))) {
                        loadedConfiguration = loadConfiguration(inputStream);
                    }
                } else {
                    return Validated.missing("config", null,
                            "Either config or configFile must be specified");
                }
            }

            Module module = loadedConfiguration.modulesByName.get(getClass().getSimpleName());
            if (module == null) {
                return Validated.missing("config", null,
                        "No matching module found in the checkstyle configuration");
            }

            this.suppressions = loadedConfiguration.suppressions;

            configure(module);

            return Validated.valid("config", this);
        } catch (IOException | CheckstyleException e) {
            return Validated.invalid("config", config == null ? configFile.getPath() : config,
                    "Checkstyle configuration could not be loaded", e);
        }
    }

    private LoadedConfiguration loadConfiguration(InputStream inputStream) throws CheckstyleException {
        Configuration checkstyleConfig = ConfigurationLoader.loadConfiguration(new InputSource(inputStream),
                name -> {
                    Object prop = properties.get(name);
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
                modules.addAll(stream(firstLevelChild.getChildren())
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
