package org.gradle.rewrite.checkstyle.policy;

import com.netflix.rewrite.internal.lang.Nullable;
import com.netflix.rewrite.tree.Cursor;
import com.netflix.rewrite.tree.Tr;
import com.netflix.rewrite.tree.Tree;

public enum Token {
    ANNOTATION_DEF((t, p) -> t instanceof Tr.Annotation),
    CLASS_DEF((t, p) -> t instanceof Tr.ClassDecl && ((Tr.ClassDecl) t).getKind() instanceof Tr.ClassDecl.Kind.Class),
    CTOR_DEF((t, p) -> t instanceof Tr.MethodDecl && ((Tr.MethodDecl) t).isConstructor()),
    ENUM_CONSTANT_DEF((t, p) -> t instanceof Tr.EnumValue),
    ENUM_DEF((t, p) -> t instanceof Tr.ClassDecl && ((Tr.ClassDecl) t).getKind() instanceof Tr.ClassDecl.Kind.Enum),
    INTERFACE_DEF((t, p) -> t instanceof Tr.ClassDecl && ((Tr.ClassDecl) t).getKind() instanceof Tr.ClassDecl.Kind.Interface),
    LAMBDA((t, p) -> t instanceof Tr.Lambda),
    LITERAL_CASE((t, p) -> t instanceof Tr.Case &&
            (((Tr.Case) t).getPattern() != null &&
                    !"default".equals(((Tr.Case) t).getPattern().printTrimmed()))),
    LITERAL_CATCH((t, p) -> t instanceof Tr.Catch),
    LITERAL_DEFAULT((t, p) -> t instanceof Tr.Case &&
            (((Tr.Case) t).getPattern() != null &&
                    "default".equals(((Tr.Case) t).getPattern().printTrimmed()))),
    LITERAL_DO((t, p) -> t instanceof Tr.DoWhileLoop),
    LITERAL_ELSE((t, p) -> t instanceof Tr.If.Else),
    LITERAL_FINALLY((t, p) -> t instanceof Tr.Try.Finally),
    LITERAL_FOR((t, p) -> t instanceof Tr.ForLoop),
    LITERAL_IF((t, p) -> t instanceof Tr.If),
    LITERAL_SWITCH((t, p) -> t instanceof Tr.Switch),
    LITERAL_SYNCHRONIZED((t, p) -> t instanceof Tr.Synchronized),
    LITERAL_TRY((t, p) -> t instanceof Tr.Try),
    LITERAL_WHILE((t, p) -> t instanceof Tr.WhileLoop),
    METHOD_DEF((t, p) -> t instanceof Tr.MethodDecl),
    OBJBLOCK((t, p) -> t instanceof Tr.Block && p.getTree() instanceof Tr.ClassDecl),
    STATIC_INIT((t, p) -> t instanceof Tr.Block && ((Tr.Block<?>) t).getStatic() != null),
    INSTANCE_INIT((t, p) -> t instanceof Tr.NewClass),
    ARRAY_INIT((t, p) -> t instanceof Tr.NewArray),
    VARIABLE_DEF((t, p) -> t instanceof Tr.VariableDecls.NamedVar),
    PARAMETER_DEF((t, p) -> p.getParentOrThrow().getTree() instanceof Tr.MethodDecl);

    public interface TokenMatcher {
        boolean matchesNotNullCursor(Tree tree, Cursor parent);

        default boolean matches(Tree tree, @Nullable Cursor parentCursor) {
            return parentCursor != null && matchesNotNullCursor(tree, parentCursor);
        }
    }

    private final TokenMatcher matcher;

    Token(TokenMatcher matcher) {
        this.matcher = matcher;
    }

    public TokenMatcher getMatcher() {
        return matcher;
    }
}
