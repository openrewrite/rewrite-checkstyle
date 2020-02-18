package org.gradle.rewrite.checkstyle;

import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import org.gradle.rewrite.checkstyle.check.*;
import org.gradle.rewrite.checkstyle.policy.BlockPolicy;
import org.gradle.rewrite.checkstyle.policy.Token;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class RewriteCheckstyle {
    public static List<RefactorVisitor> fromConfiguration(InputStream reader) {
        try {
            Configuration config = ConfigurationLoader.loadConfiguration(new InputSource(reader),
                    System::getProperty,
                    ConfigurationLoader.IgnoredModulesOptions.OMIT);

            for (Configuration firstLevelChild : config.getChildren()) {
                if ("TreeWalker".equals(firstLevelChild.getName())) {
                    return Arrays.stream(firstLevelChild.getChildren())
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
                            .map(m -> {
                                switch (m.getName()) {
                                    case "CovariantEquals":
                                        return new CovariantEquals();
                                    case "DefaultComesLast":
                                        return new DefaultComesLast(m.prop("skipIfLastAndSharedWithCase", false));
                                    case "SimplifyBooleanExpression":
                                        return new SimplifyBooleanExpression();
                                    case "SimplifyBooleanReturn":
                                        return new SimplifyBooleanReturn();
                                    case "EmptyBlock":
                                        String option = m.prop("option", "statement");
                                        return EmptyBlock.builder()
                                                .block(BlockPolicy.valueOf(option.substring(0, 1).toUpperCase() + option.substring(1)))
                                                .tokens(Arrays.stream(m.prop("tokens", "LITERAL_WHILE, LITERAL_TRY, LITERAL_FINALLY, LITERAL_DO, LITERAL_IF, LITERAL_ELSE, LITERAL_FOR, INSTANCE_INIT, STATIC_INIT, LITERAL_SWITCH, LITERAL_SYNCHRONIZED")
                                                        .split("\\s*,\\s*")).map(Token::valueOf).collect(toSet()))
                                                .build();
                                    default:
                                        return null;
                                }
                            })
                            .filter(Objects::nonNull)
                            .collect(toList());
                }
            }
        } catch (CheckstyleException e) {
            throw new RuntimeException(e);
        }
        return emptyList();
    }

    public static class Module {
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

        public String prop(String key, String defaultValue) {
            return properties.getOrDefault(key, defaultValue);
        }
    }
}
