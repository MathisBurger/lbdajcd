package satdeps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static satdeps.ExampleA.CIRCLE;
import static satdeps.ExampleA.CIRCLE_AREA;
import static satdeps.ExampleA.REPORT_TEST_TOTAL;
import static satdeps.ExampleA.SQUARE;
import static satdeps.ExampleA.SQUARE_AREA;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import satdeps.encode.FeatureModel;
import satdeps.extract.Extractor;
import satdeps.solve.DeletionQueries;

// Sources of Example A -> Extractor (layers 1-2 only) -> queries. Expected answers are
// the hand-derived ones of docs/encoding.md, Section 5, "End to end with extractor
// layers 1-2 only". Where they differ from the full model, the comments say why.
class EndToEndTest {

    private DeletionQueries queries;

    @BeforeEach
    void extractExampleA() throws Exception {
        FeatureModel model = new Extractor().extract(ExampleA.SOURCES);
        assertEquals(Set.of(), model.roots());
        queries = new DeletionQueries(model);
    }

    @Test
    void squareMatchesFullModel() throws Exception {
        Set<String> d = Set.of(SQUARE);
        assertTrue(queries.deletableAlone(d));
        assertTrue(queries.deletableWithCascade(d));
        assertEquals(Set.of(SQUARE, SQUARE_AREA), queries.cascade(d));
        assertEquals(Set.of(), queries.brokenRoots(d));
    }

    @Test
    void circleOnlyQ1MatchesBecauseRootsAreMissing() throws Exception {
        Set<String> d = Set.of(CIRCLE);
        assertFalse(queries.deletableAlone(d));
        // Full model: not deletable, ReportTest in the cascade and broken, MUS as explanation.
        assertTrue(queries.deletableWithCascade(d));
        assertEquals(Set.of(CIRCLE, CIRCLE_AREA, REPORT_TEST_TOTAL), queries.cascade(d));
        assertEquals(Set.of(), queries.brokenRoots(d));
        assertEquals(List.of(), queries.explain(d));
    }

    @Test
    void squareAreaQ1WrongBecauseImplementationObligationIsMissing() throws Exception {
        Set<String> d = Set.of(SQUARE_AREA);
        // Full model: not deletable alone, and Square goes with it.
        assertTrue(queries.deletableAlone(d));
        assertTrue(queries.deletableWithCascade(d));
        assertEquals(Set.of(SQUARE_AREA), queries.cascade(d));
        assertEquals(Set.of(), queries.brokenRoots(d));
    }
}
