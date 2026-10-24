package satdeps;

import java.nio.file.Path;

import satdeps.encode.FeatureModel;

public final class ExampleA {

    public static final Path SOURCES = Path.of("src/test/resources/exampleA");

    public static final String ROOT = "ROOT";
    public static final String PACKAGE = "p";
    public static final String SHAPE = "p.Shape";
    public static final String SHAPE_AREA = "p.Shape#area()";
    public static final String CIRCLE = "p.Circle";
    public static final String CIRCLE_AREA = "p.Circle#area()";
    public static final String SQUARE = "p.Square";
    public static final String SQUARE_AREA = "p.Square#area()";
    public static final String REPORT = "p.Report";
    public static final String REPORT_TOTAL = "p.Report#total(Shape)";
    public static final String REPORT_TEST = "p.ReportTest";
    public static final String REPORT_TEST_TOTAL = "p.ReportTest#total()";

    private ExampleA() {
    }

    public static FeatureModel tree() {
        FeatureModel model = new FeatureModel();
        model.addProjectRoot(ROOT);
        model.addFeature(PACKAGE, ROOT);
        model.addFeature(SHAPE, PACKAGE);
        model.addFeature(SHAPE_AREA, SHAPE);
        model.addFeature(CIRCLE, PACKAGE);
        model.addFeature(CIRCLE_AREA, CIRCLE);
        model.addFeature(SQUARE, PACKAGE);
        model.addFeature(SQUARE_AREA, SQUARE);
        model.addFeature(REPORT, PACKAGE);
        model.addFeature(REPORT_TOTAL, REPORT);
        model.addFeature(REPORT_TEST, PACKAGE);
        model.addFeature(REPORT_TEST_TOTAL, REPORT_TEST);
        return model;
    }

    public static FeatureModel full() {
        FeatureModel model = tree();
        model.requires("inheritance", CIRCLE, SHAPE);
        model.requires("inheritance", SQUARE, SHAPE);
        model.requires("implementation obligation", CIRCLE, CIRCLE_AREA);
        model.requires("implementation obligation", SQUARE, SQUARE_AREA);
        model.requires("or-group", SHAPE, CIRCLE, SQUARE);
        model.requires("parameter type", REPORT_TOTAL, SHAPE);
        model.requires("call s.area(): declaration", REPORT_TOTAL, SHAPE_AREA);
        model.requires("call s.area(): implementation", REPORT_TOTAL, CIRCLE_AREA, SQUARE_AREA);
        model.requires("call Report.total", REPORT_TEST_TOTAL, REPORT_TOTAL);
        model.requires("new Circle()", REPORT_TEST_TOTAL, CIRCLE);
        model.addRoot(REPORT_TEST);
        model.requires("root class requires members", REPORT_TEST, REPORT_TEST_TOTAL);
        return model;
    }
}
