package satdeps.extract;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedValueDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import satdeps.encode.FeatureModel;

public final class Extractor {

    public static final String PROJECT_ROOT = "ROOT";
    static final String DEFAULT_PACKAGE = "(default)";

    private FeatureModel model;
    private Map<Node, String> featureOf;
    private Map<String, String> typeFeatures;
    private Set<String> clauses;
    private List<String> warnings;

    public FeatureModel extract(Path sourceRoot) throws IOException {
        model = new FeatureModel();
        featureOf = new IdentityHashMap<>();
        typeFeatures = new HashMap<>();
        clauses = new HashSet<>();
        warnings = new ArrayList<>();

        List<CompilationUnit> units = parseAll(sourceRoot);
        model.addProjectRoot(PROJECT_ROOT);
        addPackages(units);
        for (CompilationUnit unit : units) {
            String pkg = packageName(unit);
            String prefix = pkg.equals(DEFAULT_PACKAGE) ? "" : pkg + ".";
            for (TypeDeclaration<?> type : unit.getTypes()) {
                addType(type, prefix + type.getNameAsString(), prefix + type.getNameAsString(), pkg);
            }
        }
        // References need the complete tree, because they may point to any feature.
        units.forEach(this::addReferences);
        return model;
    }

    public List<String> warnings() {
        return List.copyOf(warnings);
    }

    private List<CompilationUnit> parseAll(Path sourceRoot) throws IOException {
        CombinedTypeSolver types = new CombinedTypeSolver(new ReflectionTypeSolver(), new JavaParserTypeSolver(sourceRoot));
        JavaParser parser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(types)));
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceRoot)) {
            // Sorted so that variable numbering is deterministic across runs.
            files = walk.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
        List<CompilationUnit> units = new ArrayList<>();
        for (Path file : files) {
            ParseResult<CompilationUnit> result = parser.parse(file);
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                throw new IOException("cannot parse " + file + ": " + result.getProblems());
            }
            units.add(result.getResult().get());
        }
        return units;
    }

    private void addPackages(List<CompilationUnit> units) {
        TreeSet<String> packages = units.stream()
                .map(Extractor::packageName)
                .collect(Collectors.toCollection(TreeSet::new));
        List<String> byLength = packages.stream().sorted(Comparator.comparingInt(String::length)).toList();
        for (String pkg : byLength) {
            String parent = null;
            for (String candidate : byLength) {
                if (pkg.startsWith(candidate + ".")) {
                    parent = candidate; // byLength is ascending, so the last match is the longest
                }
            }
            model.addFeature(pkg, parent == null ? PROJECT_ROOT : parent);
        }
    }

    private void addType(TypeDeclaration<?> type, String name, String javaName, String parent) {
        add(type, name, parent);
        typeFeatures.put(javaName, name);
        if (type instanceof EnumDeclaration e) {
            e.getEntries().forEach(c -> add(c, name + "#" + c.getNameAsString(), name));
        }
        if (type instanceof RecordDeclaration r) {
            r.getParameters().forEach(p -> add(p, name + "#" + p.getNameAsString(), name));
        }
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof FieldDeclaration f) {
                f.getVariables().forEach(v -> add(v, name + "#" + v.getNameAsString(), name));
            } else if (member instanceof ConstructorDeclaration c) {
                add(c, name + "#<init>" + signature(c), name);
            } else if (member instanceof MethodDeclaration m) {
                add(m, name + "#" + m.getNameAsString() + signature(m), name);
            } else if (member instanceof AnnotationMemberDeclaration a) {
                add(a, name + "#" + a.getNameAsString() + "()", name);
            } else if (member instanceof TypeDeclaration<?> nested) {
                addType(nested, name + "$" + nested.getNameAsString(),
                        javaName + "." + nested.getNameAsString(), name);
            }
        }
    }

    private void add(Node declaration, String name, String parent) {
        model.addFeature(name, parent);
        featureOf.put(declaration, name);
    }

    private static String signature(CallableDeclaration<?> callable) {
        return callable.getParameters().stream()
                .map(Extractor::parameterType)
                .collect(Collectors.joining(",", "(", ")"));
    }

    private static String parameterType(Parameter p) {
        return typeName(p.getType()) + (p.isVarArgs() ? "[]" : "");
    }

    private static String typeName(Type type) {
        if (type instanceof ClassOrInterfaceType c) {
            return c.getNameWithScope();
        }
        if (type instanceof ArrayType a) {
            return typeName(a.getComponentType()) + "[]";
        }
        return type.asString();
    }

    private static String packageName(CompilationUnit unit) {
        return unit.getPackageDeclaration().map(NodeWithName::getNameAsString).orElse(DEFAULT_PACKAGE);
    }

    private void addReferences(CompilationUnit unit) {
        for (ClassOrInterfaceType type : unit.findAll(ClassOrInterfaceType.class)) {
            resolveType(type).ifPresent(target -> require(isInheritance(type) ? "inheritance" : "type usage", type, target));
        }
        for (AnnotationExpr annotation : unit.findAll(AnnotationExpr.class)) {
            try {
                feature(annotation.resolve().getQualifiedName())
                        .ifPresent(target -> require("annotation", annotation, target));
            } catch (RuntimeException e) {
                warn("unresolved annotation", annotation);
            }
        }
        for (ObjectCreationExpr creation : unit.findAll(ObjectCreationExpr.class)) {
            // For an anonymous class the created type is already covered as a type usage.
            if (creation.getAnonymousClassBody().isEmpty()) {
                constructorCall(creation, creation::resolve);
            }
        }
        for (ExplicitConstructorInvocationStmt call : unit.findAll(ExplicitConstructorInvocationStmt.class)) {
            constructorCall(call, call::resolve);
        }
        for (FieldAccessExpr access : unit.findAll(FieldAccessExpr.class)) {
            fieldAccess(access, access::resolve);
        }
        for (NameExpr name : unit.findAll(NameExpr.class)) {
            fieldAccess(name, name::resolve);
        }
    }

    private Optional<String> resolveType(ClassOrInterfaceType type) {
        try {
            ResolvedType resolved = type.resolve();
            if (resolved.isReferenceType()) {
                return feature(resolved.asReferenceType().getQualifiedName());
            }
        } catch (RuntimeException e) {
            warn("unresolved type", type);
        }
        // Type variables, wildcards and unresolved types are not modelled.
        return Optional.empty();
    }

    private static boolean isInheritance(ClassOrInterfaceType type) {
        return type.getParentNode()
                .filter(p -> p instanceof ClassOrInterfaceDeclaration d
                        && (d.getExtendedTypes().contains(type) || d.getImplementedTypes().contains(type)))
                .isPresent();
    }

    private void constructorCall(Node call, Supplier<ResolvedConstructorDeclaration> resolve) {
        try {
            ResolvedConstructorDeclaration constructor = resolve.get();
            Optional<String> type = feature(constructor.declaringType().getQualifiedName());
            if (type.isEmpty()) {
                return;
            }
            String target = constructor.toAst(ConstructorDeclaration.class)
                    .map(c -> type.get() + "#<init>" + signature(c))
                    .orElse(type.get());
            require("constructor call", call, target);
        } catch (RuntimeException e) {
            warn("unresolved constructor call", call);
        }
    }

    private void fieldAccess(Expression access, Supplier<ResolvedValueDeclaration> resolve) {
        try {
            ResolvedValueDeclaration value = resolve.get();
            Optional<String> owner = Optional.empty();
            if (value.isField()) {
                owner = feature(value.asField().declaringType().getQualifiedName());
            } else if (value.isEnumConstant() && value.getType().isReferenceType()) {
                owner = feature(value.getType().asReferenceType().getQualifiedName());
            }
            owner.ifPresent(o -> require("field access", access, o + "#" + value.getName()));
        } catch (RuntimeException e) {
            if (!isScope(access)) {
                warn("unresolved name", access);
            }
        }
    }

    private static boolean isScope(Expression e) {
        return e.getParentNode().filter(p ->
                (p instanceof FieldAccessExpr f && f.getScope() == e)
                || (p instanceof MethodCallExpr m && m.getScope().filter(s -> s == e).isPresent())).isPresent();
    }

    private Optional<String> feature(String qualifiedName) {
        return Optional.ofNullable(typeFeatures.get(qualifiedName));
    }

    private void require(String reason, Node reference, String target) {
        if (!model.hasFeature(target)) {
            warn("reference to unknown feature " + target, reference);
            return;
        }
        for (String source : sources(reference)) {
            if (model.isInSubtree(model.id(source), model.id(target)) || !clauses.add(source + " -> " + target)) {
                continue;
            }
            model.requires(reason + " " + source + " -> " + target, source, target);
        }
    }

    private List<String> sources(Node node) {
        for (Optional<Node> current = Optional.of(node); current.isPresent(); current = current.get().getParentNode()) {
            Node n = current.get();
            String feature = featureOf.get(n);
            if (feature != null) {
                return List.of(feature);
            }
            if (n instanceof FieldDeclaration f) {
                return f.getVariables().stream().map(featureOf::get).toList();
            }
        }
        return List.of();
    }

    private void warn(String message, Node node) {
        String where = sources(node).stream().findFirst().orElse("?");
        warnings.add(message + " '" + node + "' in " + where);
    }
}
