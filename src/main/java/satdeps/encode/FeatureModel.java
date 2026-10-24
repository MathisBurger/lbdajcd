package satdeps.encode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FeatureModel {

    private final List<String> names = new ArrayList<>();
    private final Map<String, Integer> ids = new HashMap<>();
    private final Map<Integer, Integer> parents = new HashMap<>();
    private final List<Constraint> constraints = new ArrayList<>();
    private final Set<Integer> roots = new LinkedHashSet<>();

    public void addProjectRoot(String name) {
        int id = newFeature(name);
        constraints.add(new Constraint("project root " + name, new int[] {id}));
    }

    public void addFeature(String name, String parent) {
        int parentId = id(parent);
        int id = newFeature(name);
        parents.put(id, parentId);
        constraints.add(new Constraint("tree " + name + " -> " + parent, new int[] {-id, parentId}));
    }

    public void requires(String reason, String source, String... anyOf) {
        int[] literals = new int[anyOf.length + 1];
        literals[0] = -id(source);
        for (int i = 0; i < anyOf.length; i++) {
            literals[i + 1] = id(anyOf[i]);
        }
        constraints.add(new Constraint(reason, literals));
    }

    public void addRoot(String name) {
        roots.add(id(name));
    }

    public int id(String name) {
        Integer id = ids.get(name);
        if (id == null) {
            throw new IllegalArgumentException("unknown feature: " + name);
        }
        return id;
    }

    public String name(int id) {
        return names.get(id - 1);
    }

    public int featureCount() {
        return names.size();
    }

    public boolean hasFeature(String name) {
        return ids.containsKey(name);
    }

    public List<String> featureNames() {
        return Collections.unmodifiableList(names);
    }

    public String parent(String name) {
        Integer parent = parents.get(id(name));
        return parent == null ? null : name(parent);
    }

    public List<Constraint> constraints() {
        return Collections.unmodifiableList(constraints);
    }

    public List<Constraint> rootConstraints() {
        return roots.stream().map(r -> new Constraint("root " + name(r), new int[] {r})).toList();
    }

    public Set<Integer> roots() {
        return Collections.unmodifiableSet(roots);
    }

    public boolean isInSubtree(int id, int ancestor) {
        for (Integer current = id; current != null; current = parents.get(current)) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private int newFeature(String name) {
        if (ids.containsKey(name)) {
            throw new IllegalArgumentException("duplicate feature: " + name);
        }
        names.add(name);
        ids.put(name, names.size());
        return names.size();
    }
}
