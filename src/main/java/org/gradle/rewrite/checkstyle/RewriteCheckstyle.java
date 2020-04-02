package org.gradle.rewrite.checkstyle;

import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.api.*;
import com.puppycrawl.tools.checkstyle.filters.SuppressionsLoader;
import org.gradle.rewrite.checkstyle.check.*;
import org.gradle.rewrite.checkstyle.policy.*;
import org.openrewrite.Refactor;
import org.openrewrite.RefactorModule;
import org.openrewrite.SourceVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.tree.J;
import org.xml.sax.InputSource;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.gradle.rewrite.checkstyle.policy.OperatorToken.*;
import static org.gradle.rewrite.checkstyle.policy.ParenthesesToken.*;
import static org.gradle.rewrite.checkstyle.policy.PunctuationToken.*;
import static org.gradle.rewrite.checkstyle.policy.Token.*;

public class RewriteCheckstyle implements RefactorModule<J.CompilationUnit, J> {
    // we just want to re-use the suppression filtering logic in Checkstyle without emitting messages
    private static final LocalizedMessage localizedMessageThatDoesntMatter = new LocalizedMessage(1,
            "bundle", "key", new String[0], null, RewriteCheckstyle.class, null);

    private List<SourceVisitor<J>> checkstyleVisitors = emptyList();
    private FilterSet suppressions = new FilterSet();

    public RewriteCheckstyle(InputStream reader) {
        this(reader, emptySet(), null);
    }

    public RewriteCheckstyle(InputStream reader, Set<String> excludes,
                             @Nullable Map<String, Object> configProperties) {
        try {
            Configuration config = ConfigurationLoader.loadConfiguration(new InputSource(reader),
                    configProperties == null ?
                            System::getProperty :
                            name -> {
                                Object prop = configProperties.get(name);
                                return prop == null ?
                                        name.equals("config_loc") ? "config/checkstyle" : null :
                                        prop.toString();
                            },
                    ConfigurationLoader.IgnoredModulesOptions.OMIT);

            for (Configuration firstLevelChild : config.getChildren()) {
                if ("SuppressionFilter".equals(firstLevelChild.getName())) {
                    for (String attributeName : firstLevelChild.getAttributeNames()) {
                        if("file".equals(attributeName)) {
                           this.suppressions = SuppressionsLoader.loadSuppressions(firstLevelChild.getAttribute("file"));
                        }
                    }

                }
            }

            initCheckstyleVisitors(config, excludes);
        } catch (CheckstyleException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Refactor<J.CompilationUnit, J> apply(Refactor<J.CompilationUnit, J> refactor) {
        if(suppressions.accept(new AuditEvent("does not matter", refactor.getOriginal().getSourcePath(),
                localizedMessageThatDoesntMatter))) {
            return refactor.visit(checkstyleVisitors);
        }
        return refactor;
    }

    private void initCheckstyleVisitors(Configuration config, Set<String> excludes) {
        for (Configuration firstLevelChild : config.getChildren()) {
            if ("TreeWalker".equals(firstLevelChild.getName())) {
                this.checkstyleVisitors = stream(firstLevelChild.getChildren())
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
                        .filter(m -> !excludes.contains(m.getName()))
                        .map(m -> {
                            switch (m.getName()) {
                                case "CovariantEquals":
                                    return new CovariantEquals();
                                case "DefaultComesLast":
                                    return new DefaultComesLast(m.prop("skipIfLastAndSharedWithCase", false));
                                case "EmptyBlock":
                                    return EmptyBlock.builder()
                                            .block(m.valueOfOrElse(BlockPolicy::valueOf, BlockPolicy.Statement))
                                            .tokens(m.propAsTokens(Token.class, Set.of(
                                                    LITERAL_WHILE,
                                                    LITERAL_TRY,
                                                    LITERAL_FINALLY,
                                                    LITERAL_DO,
                                                    LITERAL_IF,
                                                    LITERAL_ELSE,
                                                    LITERAL_FOR,
                                                    INSTANCE_INIT,
                                                    STATIC_INIT,
                                                    LITERAL_SWITCH,
                                                    Token.LITERAL_SYNCHRONIZED
                                            )))
                                            .build();
                                case "EmptyForInitializerPad":
                                    return new EmptyForInitializerPad(m.valueOfOrElse(PadPolicy::valueOf,
                                            PadPolicy.NOSPACE));
                                case "EmptyForIteratorPad":
                                    return new EmptyForIteratorPad(m.valueOfOrElse(PadPolicy::valueOf,
                                            PadPolicy.NOSPACE));
                                case "EmptyStatement":
                                    return new EmptyStatement();
                                case "EqualsAvoidsNull":
                                    return new EqualsAvoidsNull(m.prop("ignoreEqualsIgnoreCase", false));
                                case "ExplicitInitialization":
                                    return new ExplicitInitialization(m.prop("onlyObjectReferences", false));
                                case "FallThrough":
                                    return FallThrough.builder()
                                            .checkLastCaseGroup(m.prop("checkLastCaseGroup", false))
                                            .reliefPattern(m.prop("reliefPattern", Pattern.compile("falls?[ -]?thr(u|ough)")))
                                            .build();
                                case "FinalClass":
                                    return new FinalClass();
                                case "FinalLocalVariable":
                                    return new FinalLocalVariable();
                                case "GenericWhitespace":
                                    return new GenericWhitespace();
                                case "HiddenField":
                                    return HiddenField.builder()
                                            .ignoreAbstractMethods(m.prop("ignoreAbstractMethods", false))
                                            .ignoreFormat(m.prop("ignoreFormat", null))
                                            .ignoreSetter(m.prop("ignoreSetter", false))
                                            .ignoreConstructorParameter(m.prop("ignoreConstructorParameter", false))
                                            .setterCanReturnItsClass(m.prop("setterCanReturnItsClass", false))
                                            .tokens(m.propAsTokens(Token.class, Set.of(VARIABLE_DEF, PARAMETER_DEF, Token.LAMBDA)))
                                            .build();
                                case "HideUtilityClassConstructor":
                                    return new HideUtilityClassConstructor();
                                case "LeftCurly":
                                    return LeftCurly.builder()
                                            .ignoreEnums(m.prop("ignoreEnums", false))
                                            .option(m.valueOfOrElse(LeftCurlyPolicy::valueOf, LeftCurlyPolicy.EOL))
                                            .build();
                                case "MethodParamPad":
                                    return MethodParamPad.builder()
                                            .allowLineBreaks(m.prop("allowLineBreaks", false))
                                            .option(m.valueOfOrElse(PadPolicy::valueOf, PadPolicy.NOSPACE))
                                            .build();
                                case "MultipleVariableDeclarations":
                                    return new MultipleVariableDeclarations();
                                case "NeedBraces":
                                    return NeedBraces.builder()
                                            .allowEmptyLoopBody(m.prop("allowEmptyLoopBody", false))
                                            .allowSingleLineStatement(m.prop("allowSingleLineStatement", false))
                                            .build();
                                case "NoFinalizer":
                                    return new NoFinalizer();
                                case "NoWhitespaceAfter":
                                    return NoWhitespaceAfter.builder()
                                            .allowLineBreaks(m.prop("allowLineBreaks", false))
                                            .tokens(m.propAsTokens(PunctuationToken.class,
                                                    Set.of(PunctuationToken.ARRAY_INIT, AT, INC, DEC, UNARY_MINUS, UNARY_PLUS, BNOT, LNOT, DOT, ARRAY_DECLARATOR, INDEX_OP)))
                                            .build();
                                case "NoWhitespaceBefore":
                                    return NoWhitespaceBefore.builder()
                                            .allowLineBreaks(m.prop("allowLineBreaks", false))
                                            .tokens(m.propAsTokens(PunctuationToken.class,
                                                    Set.of(COMMA, SEMI, POST_INC, POST_DEC, ELLIPSIS)))
                                            .build();
                                case "OperatorWrap":
                                    return OperatorWrap.builder()
                                            .option(m.valueOfOrElse(WrapPolicy::valueOf, WrapPolicy.NL))
                                            .tokens(m.propAsTokens(OperatorToken.class, Set.of(QUESTION, COLON, EQUAL, NOT_EQUAL, DIV, PLUS, MINUS, STAR, MOD, SR, BSR, GE, GT, SL, LE, LT, BXOR, BOR, LOR, BAND, LAND, TYPE_EXTENSION_AND, LITERAL_INSTANCEOF)))
                                            .build();
                                case "RightCurly":
                                    return RightCurly.builder()
                                            .option(m.valueOfOrElse(RightCurlyPolicy::valueOf, RightCurlyPolicy.SAME))
                                            .tokens(m.propAsTokens(Token.class, Set.of(LITERAL_TRY, LITERAL_CATCH, LITERAL_FINALLY, LITERAL_IF, LITERAL_ELSE)))
                                            .build();
                                case "SimplifyBooleanExpression":
                                    return new SimplifyBooleanExpression();
                                case "SimplifyBooleanReturn":
                                    return new SimplifyBooleanReturn();
                                case "StaticVariableName":
                                    return StaticVariableName.builder()
                                            .applyToPrivate(m.prop("applyToPrivate", true))
                                            .applyToPackage(m.prop("applyToPackage", true))
                                            .applyToProtected(m.prop("applyToProtected", true))
                                            .applyToPublic(m.prop("applyToPublic", true))
                                            .build();
                                case "StringLiteralEquality":
                                    return new StringLiteralEquality();
                                case "TypecastParenPad":
                                    return new TypecastParenPad(m.valueOfOrElse(PadPolicy::valueOf,
                                            PadPolicy.NOSPACE));
                                case "UnnecessaryParentheses":
                                    return UnnecessaryParentheses.builder()
                                            .tokens(m.propAsTokens(ParenthesesToken.class, Set.of(
                                                    EXPR,
                                                    IDENT,
                                                    NUM_DOUBLE,
                                                    NUM_FLOAT,
                                                    NUM_INT,
                                                    NUM_LONG,
                                                    STRING_LITERAL,
                                                    LITERAL_NULL,
                                                    LITERAL_FALSE,
                                                    LITERAL_TRUE,
                                                    ParenthesesToken.ASSIGN,
                                                    ParenthesesToken.BAND_ASSIGN,
                                                    ParenthesesToken.BOR_ASSIGN,
                                                    ParenthesesToken.BSR_ASSIGN,
                                                    ParenthesesToken.BXOR_ASSIGN,
                                                    ParenthesesToken.DIV_ASSIGN,
                                                    ParenthesesToken.MINUS_ASSIGN,
                                                    ParenthesesToken.MOD_ASSIGN,
                                                    ParenthesesToken.PLUS_ASSIGN,
                                                    ParenthesesToken.SL_ASSIGN,
                                                    ParenthesesToken.SR_ASSIGN,
                                                    ParenthesesToken.STAR_ASSIGN,
                                                    ParenthesesToken.LAMBDA
                                            )))
                                            .build();
                                default:
                                    return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(toList());
            }
        }
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

        private boolean prop(String key, boolean defaultValue) {
            return properties.containsKey(key) ? Boolean.parseBoolean(properties.get(key))
                    : defaultValue;
        }

        private Pattern prop(String key, Pattern defaultValue) {
            if (properties.containsKey(key)) {
                try {
                    return Pattern.compile(key);
                } catch (Throwable ignored) {
                }
            }
            return defaultValue;
        }

        private <T extends Enum<T>> Set<T> propAsTokens(Class<T> enumType, Set<T> defaultValue) {
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

        private <T> T valueOfOrElse(Function<String, T> valueOf, T defaultValue) {
            try {
                return valueOf.apply(toUpper(properties.get("option")));
            } catch (Throwable t) {
                try {
                    return valueOf.apply(properties.get("option").toUpperCase());
                } catch(Throwable t2) {
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
