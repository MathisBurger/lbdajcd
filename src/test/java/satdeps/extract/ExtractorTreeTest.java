package satdeps.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import satdeps.ExampleA;
import satdeps.encode.FeatureModel;

class ExtractorTreeTest {

    private final Extractor extractor = new Extractor();

    @Test
    void exampleAMatchesHandBuiltTree() throws Exception {
        FeatureModel extracted = extractor.extract(ExampleA.SOURCES);
        FeatureModel expected = ExampleA.tree();

        assertEquals(parents(expected), parents(extracted));
        // Project root unit clause + one child -> parent clause per other feature.
        long treeClauses = extracted.constraints().stream()
                .filter(c -> c.reason().startsWith("tree ") || c.reason().startsWith("project root"))
                .count();
        assertEquals(expected.constraints().size(), treeClauses);
    }

    @Test
    void treeCases() throws Exception {
        FeatureModel extracted = extractor.extract(Path.of("src/test/resources/treeCases"));

        Map<String, String> expected = new HashMap<>();
        expected.put("ROOT", null);
        expected.put("q", "ROOT");
        expected.put("q.sub", "q");
        expected.put("r.s", "ROOT");
        expected.put("q.sub.Leaf", "q.sub");
        expected.put("r.s.Deep", "r.s");
        expected.put("q.Outer", "q");
        for (String member : Set.of("#a", "#b", "#<init>()", "#<init>(int)", "#run(int)", "#run(long)",
                "#generic(List,T,String[])", "$Inner", "$Mode", "$Callback", "$Point")) {
            expected.put("q.Outer" + member, "q.Outer");
        }
        expected.put("q.Outer$Inner#go()", "q.Outer$Inner");
        expected.put("q.Outer$Mode#ON", "q.Outer$Mode");
        expected.put("q.Outer$Mode#OFF", "q.Outer$Mode");
        expected.put("q.Outer$Mode#flip()", "q.Outer$Mode");
        expected.put("q.Outer$Callback#call()", "q.Outer$Callback");
        expected.put("q.Outer$Point#x", "q.Outer$Point");
        expected.put("q.Outer$Point#y", "q.Outer$Point");

        assertEquals(expected, parents(extracted));
    }

    @Test
    void numberingIsDeterministic() throws Exception {
        assertEquals(extractor.extract(ExampleA.SOURCES).featureNames(),
                extractor.extract(ExampleA.SOURCES).featureNames());
    }

    private static Map<String, String> parents(FeatureModel model) {
        Map<String, String> parents = new HashMap<>();
        Set<String> seen = new HashSet<>();
        for (String name : model.featureNames()) {
            seen.add(name);
            parents.put(name, model.parent(name));
        }
        assertEquals(model.featureCount(), seen.size());
        return parents;
    }
}
