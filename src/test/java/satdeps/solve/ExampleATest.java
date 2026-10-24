package satdeps.solve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static satdeps.ExampleA.CIRCLE;
import static satdeps.ExampleA.CIRCLE_AREA;
import static satdeps.ExampleA.REPORT_TEST;
import static satdeps.ExampleA.REPORT_TEST_TOTAL;
import static satdeps.ExampleA.SQUARE;
import static satdeps.ExampleA.SQUARE_AREA;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import satdeps.ExampleA;
import satdeps.encode.Constraint;
import satdeps.encode.FeatureModel;

class ExampleATest {

    private FeatureModel model;
    private DeletionQueries queries;

    @BeforeEach
    void buildExampleA() {
        model = ExampleA.full();
        queries = new DeletionQueries(model);
    }

    @Test
    void cnfMatchesDocumentation() {
        Set<String> expected = Set.of(
                "1",
                "-2 1", "-3 2", "-5 2", "-7 2", "-9 2", "-11 2", "-4 3", "-6 5", "-8 7", "-10 9", "-12 11",
                "-5 3", "-7 3",
                "-5 6", "-7 8",
                "-3 5 7",
                "-10 3",
                "-10 4", "-10 6 8",
                "-12 10",
                "-12 5",
                "11",
                "-11 12");
        List<Constraint> all = new ArrayList<>(model.constraints());
        all.addAll(model.rootConstraints());

        assertEquals(12, model.featureCount());
        assertEquals(24, all.size());
        assertEquals(expected, all.stream().map(ExampleATest::dimacs).collect(Collectors.toSet()));
    }

    @Test
    void squareIsDeletableBecauseCircleStillImplementsShape() throws Exception {
        Set<String> d = Set.of(SQUARE);
        assertTrue(queries.deletableAlone(d));
        assertTrue(queries.deletableWithCascade(d));
        assertEquals(Set.of(SQUARE, SQUARE_AREA), queries.cascade(d));
        assertEquals(Set.of(), queries.brokenRoots(d));
        assertEquals(List.of(), queries.explain(d));
    }

    @Test
    void circleIsNotDeletableBecauseTheTestInstantiatesIt() throws Exception {
        Set<String> d = Set.of(CIRCLE);
        assertFalse(queries.deletableAlone(d));
        assertFalse(queries.deletableWithCascade(d));
        assertEquals(Set.of(CIRCLE, CIRCLE_AREA, REPORT_TEST_TOTAL, REPORT_TEST), queries.cascade(d));
        assertEquals(Set.of(REPORT_TEST), queries.brokenRoots(d));
        assertEquals(Set.of("11", "-11 12", "-12 5", "-5"),
                queries.explain(d).stream().map(ExampleATest::dimacs).collect(Collectors.toSet()));
    }

    @Test
    void squareAreaIsDeletableOnlyTogetherWithSquare() throws Exception {
        Set<String> d = Set.of(SQUARE_AREA);
        assertFalse(queries.deletableAlone(d));
        assertTrue(queries.deletableWithCascade(d));
        assertEquals(Set.of(SQUARE_AREA, SQUARE), queries.cascade(d));
        assertEquals(Set.of(), queries.brokenRoots(d));
    }

    private static String dimacs(Constraint c) {
        return Arrays.stream(c.literals()).mapToObj(Integer::toString).collect(Collectors.joining(" "));
    }
}
