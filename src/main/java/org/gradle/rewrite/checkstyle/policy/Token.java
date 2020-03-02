package org.gradle.rewrite.checkstyle.policy;

import org.openrewrite.Cursor;
import org.openrewrite.Tree;
import org.openrewrite.java.tree.J;

import java.util.Set;

import static java.util.Arrays.stream;

public enum Token {
    ANNOTATION_DEF((t, p) -> t instanceof J.Annotation),
    CLASS_DEF((t, p) -> t instanceof J.ClassDecl && ((J.ClassDecl) t).getKind() instanceof J.ClassDecl.Kind.Class),
    CTOR_DEF((t, p) -> t instanceof J.MethodDecl && ((J.MethodDecl) t).isConstructor()),
    ENUM_CONSTANT_DEF((t, p) -> t instanceof J.EnumValue),
    ENUM_DEF((t, p) -> t instanceof J.ClassDecl && ((J.ClassDecl) t).getKind() instanceof J.ClassDecl.Kind.Enum),
    INTERFACE_DEF((t, p) -> t instanceof J.ClassDecl && ((J.ClassDecl) t).getKind() instanceof J.ClassDecl.Kind.Interface),
    LAMBDA((t, p) -> t instanceof J.Lambda),
    LITERAL_CASE((t, p) -> t instanceof J.Case &&
            (((J.Case) t).getPattern() != null &&
                    !"default".equals(((J.Case) t).getPattern().printTrimmed()))),
    LITERAL_CATCH((t, p) -> t instanceof J.Try.Catch),
    LITERAL_DEFAULT((t, p) -> t instanceof J.Case &&
            (((J.Case) t).getPattern() != null &&
                    "default".equals(((J.Case) t).getPattern().printTrimmed()))),
    LITERAL_DO((t, p) -> t instanceof J.DoWhileLoop),
    LITERAL_ELSE((t, p) -> t instanceof J.If.Else),
    LITERAL_FINALLY((t, p) -> t instanceof J.Try.Finally),
    LITERAL_FOR((t, p) -> t instanceof J.ForLoop),
    LITERAL_IF((t, p) -> t instanceof J.If),
    LITERAL_NEW((t, p) -> t instanceof J.NewClass || t instanceof J.NewArray),
    LITERAL_SWITCH((t, p) -> t instanceof J.Switch),
    LITERAL_SYNCHRONIZED((t, p) -> t instanceof J.Synchronized),
    LITERAL_TRY((t, p) -> t instanceof J.Try),
    LITERAL_WHILE((t, p) -> t instanceof J.WhileLoop),
    METHOD_CALL((t, p) -> t instanceof J.MethodInvocation),
    METHOD_DEF((t, p) -> t instanceof J.MethodDecl),
    OBJBLOCK((t, p) -> t instanceof J.Block && p.getTree() instanceof J.ClassDecl),
    STATIC_INIT((t, p) -> t instanceof J.Block && ((J.Block<?>) t).getStatic() != null),
    SUPER_CTOR_CALL((t, p) -> t instanceof J.MethodInvocation && ((J.MethodInvocation) t).getSimpleName().equals("super")),
    INSTANCE_INIT((t, p) -> t instanceof J.NewClass),
    ARRAY_INIT((t, p) -> t instanceof J.NewArray),
    VARIABLE_DEF((t, p) -> t instanceof J.VariableDecls.NamedVar),
    PARAMETER_DEF((t, p) -> p.getParentOrThrow().getTree() instanceof J.MethodDecl);

    public interface TokenMatcher {
        boolean matchesNotNullCursor(Tree tree, Cursor parent);

        default boolean matches(Cursor cursor) {
            return cursor != null && matchesNotNullCursor(cursor.getTree(), cursor.getParent());
        }
    }

    private final TokenMatcher matcher;

    Token(TokenMatcher matcher) {
        this.matcher = matcher;
    }

    public TokenMatcher getMatcher() {
        return matcher;
    }

    public static boolean matchesOneOf(Set<Token> configured, Cursor cursor, Token... tokens) {
        return stream(tokens).anyMatch(token -> configured.contains(token) && token.getMatcher().matches(cursor));
    }
}
