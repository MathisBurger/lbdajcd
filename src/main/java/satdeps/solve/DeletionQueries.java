package satdeps.solve;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sat4j.core.VecInt;
import org.sat4j.maxsat.WeightedMaxSatDecorator;
import org.sat4j.minisat.SolverFactory;
import org.sat4j.pb.OptToPBSATAdapter;
import org.sat4j.pb.PseudoOptDecorator;
import org.sat4j.specs.ContradictionException;
import org.sat4j.specs.IConstr;
import org.sat4j.specs.ISolver;
import org.sat4j.specs.TimeoutException;
import org.sat4j.tools.xplain.Xplain;

import satdeps.encode.Constraint;
import satdeps.encode.FeatureModel;

public final class DeletionQueries {

    private final FeatureModel model;

    public DeletionQueries(FeatureModel model) {
        this.model = model;
    }

    public boolean deletableAlone(Set<String> deleted) throws TimeoutException {
        Set<Integer> d = ids(deleted);
        VecInt assumptions = new VecInt();
        for (int v = 1; v <= model.featureCount(); v++) {
            if (!inDeletedSubtree(v, d)) {
                assumptions.push(v);
            }
        }
        return isSatisfiable(d, assumptions);
    }

    public boolean deletableWithCascade(Set<String> deleted) throws TimeoutException {
        return isSatisfiable(ids(deleted), new VecInt());
    }

    public Set<String> cascade(Set<String> deleted) throws TimeoutException {
        Set<Integer> d = ids(deleted);
        List<Integer> soft = new ArrayList<>();
        for (int v = 1; v <= model.featureCount(); v++) {
            if (!d.contains(v)) {
                soft.add(v);
            }
        }
        return absentInOptimum(d, soft);
    }

    public Set<String> brokenRoots(Set<String> deleted) throws TimeoutException {
        Set<String> absent = absentInOptimum(ids(deleted), new ArrayList<>(model.roots()));
        absent.retainAll(names(model.roots()));
        return absent;
    }

    public List<Constraint> explain(Set<String> deleted) throws TimeoutException {
        Xplain<ISolver> solver = new Xplain<>(SolverFactory.newDefault());
        solver.newVar(model.featureCount());
        Map<IConstr, Constraint> origin = new HashMap<>();
        try {
            for (Constraint c : allConstraints(ids(deleted))) {
                // Xplain guards every clause with a selector variable, so even
                // contradicting unit clauses are accepted here.
                origin.put(solver.addClause(new VecInt(c.literals())), c);
            }
        } catch (ContradictionException e) {
            throw new IllegalStateException("Xplain should not detect contradictions while adding clauses", e);
        }
        if (solver.isSatisfiable()) {
            return List.of();
        }
        return solver.explain().stream().map(origin::get).toList();
    }

    private boolean isSatisfiable(Set<Integer> deleted, VecInt assumptions) throws TimeoutException {
        ISolver solver = SolverFactory.newDefault();
        solver.newVar(model.featureCount());
        try {
            for (Constraint c : allConstraints(deleted)) {
                solver.addClause(new VecInt(c.literals()));
            }
        } catch (ContradictionException e) {
            // Sat4j reports conflicts found during unit propagation at load time this way.
            return false;
        }
        return solver.isSatisfiable(assumptions);
    }

    private Set<String> absentInOptimum(Set<Integer> deleted, List<Integer> soft)
            throws TimeoutException {
        WeightedMaxSatDecorator maxsat = new WeightedMaxSatDecorator(org.sat4j.pb.SolverFactory.newDefault());
        maxsat.newVar(model.featureCount());
        try {
            for (Constraint c : model.constraints()) {
                maxsat.addHardClause(new VecInt(c.literals()));
            }
            for (int d : deleted) {
                maxsat.addHardClause(new VecInt(new int[] {-d}));
            }
            for (int v : soft) {
                maxsat.addSoftClause(1, new VecInt(new int[] {v}));
            }
        } catch (ContradictionException e) {
            throw new IllegalStateException("hard constraints are unsatisfiable", e);
        }
        OptToPBSATAdapter optimiser = new OptToPBSATAdapter(new PseudoOptDecorator(maxsat));
        if (!optimiser.isSatisfiable()) {
            throw new IllegalStateException("hard constraints are unsatisfiable");
        }
        Set<String> absent = new LinkedHashSet<>();
        for (int v = 1; v <= model.featureCount(); v++) {
            if (!optimiser.model(v)) {
                absent.add(model.name(v));
            }
        }
        return absent;
    }

    private List<Constraint> allConstraints(Set<Integer> deleted) {
        List<Constraint> all = new ArrayList<>(model.constraints());
        all.addAll(model.rootConstraints());
        for (int d : deleted) {
            all.add(new Constraint("delete " + model.name(d), new int[] {-d}));
        }
        return all;
    }

    private boolean inDeletedSubtree(int v, Set<Integer> deleted) {
        return deleted.stream().anyMatch(d -> model.isInSubtree(v, d));
    }

    private Set<Integer> ids(Set<String> names) {
        Set<Integer> ids = new LinkedHashSet<>();
        names.forEach(n -> ids.add(model.id(n)));
        return ids;
    }

    private Set<String> names(Set<Integer> ids) {
        Set<String> names = new LinkedHashSet<>();
        ids.forEach(i -> names.add(model.name(i)));
        return names;
    }
}
