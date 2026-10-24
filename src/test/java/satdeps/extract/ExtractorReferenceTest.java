package satdeps.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import satdeps.ExampleA;
import satdeps.encode.Constraint;
import satdeps.encode.FeatureModel;

class ExtractorReferenceTest {

    private final Extractor extractor = new Extractor();

    @Test
    void exampleAPlainReferences() throws Exception {
        FeatureModel extracted = extractor.extract(ExampleA.SOURCES);

        assertEquals(Set.of(
                        "-p.Circle p.Shape",
                        "-p.Square p.Shape",
                        "-p.Report#total(Shape) p.Shape",
                        "-p.ReportTest#total() p.Circle"),
                crossTree(extracted));
        // JUnit is not on the type solver's path.
        assertEquals(List.of("unresolved annotation '@Test' in p.ReportTest#total()"), extractor.warnings());
    }

    @Test
    void referenceCases() throws Exception {
        FeatureModel extracted = extractor.extract(Path.of("src/test/resources/refCases"));

        assertEquals(Set.of(
                        // Base(int v) { value = v; }
                        "-s.Base#<init>(int) s.Base#value",
                        // @Marker class User extends Base implements Api
                        "-s.User s.Marker",
                        "-s.User s.Base",
                        "-s.User s.Api",
                        // List<Plain> plains; Mode mode = Mode.ON;
                        "-s.User#plains s.Plain",
                        "-s.User#mode s.User$Mode",
                        "-s.User#mode s.User$Mode#ON",
                        // static { Base.COUNT = 1; } -> attributed to the class
                        "-s.User s.Base#COUNT",
                        // User() { super(1); }
                        "-s.User#<init>() s.Base#<init>(int)",
                        // call(): new Plain(), cast, Base.class, value, lambda, anonymous class
                        "-s.User#call() s.Plain",
                        "-s.User#call() s.Base",
                        "-s.User#call() s.Base#value",
                        "-s.User#call() s.Base#<init>()",
                        "-s.User#call() s.Api",
                        "-s.User#call() s.Base#COUNT"),
                crossTree(extracted));
        assertEquals(List.of(), extractor.warnings());
    }

    private static Set<String> crossTree(FeatureModel model) {
        Set<String> clauses = new HashSet<>();
        for (Constraint c : model.constraints()) {
            if (!c.reason().startsWith("tree ") && !c.reason().startsWith("project root")) {
                clauses.add(Arrays.stream(c.literals())
                        .mapToObj(l -> (l < 0 ? "-" : "") + model.name(Math.abs(l)))
                        .collect(Collectors.joining(" ")));
            }
        }
        return clauses;
    }
}
