package org.gradle.rewrite.checkstyle;

import com.netflix.rewrite.tree.visitor.refactor.RefactorVisitor;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import org.gradle.rewrite.checkstyle.check.CovariantEquals;
import org.gradle.rewrite.checkstyle.check.DefaultComesLast;
import org.gradle.rewrite.checkstyle.check.SimplifyBooleanExpression;
import org.gradle.rewrite.checkstyle.check.SimplifyBooleanReturn;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

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
    }
}
