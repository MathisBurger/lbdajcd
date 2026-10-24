package satdeps.encode;

import java.util.Arrays;

public record Constraint(String reason, int[] literals) {

    public Constraint {
        literals = literals.clone();
    }

    @Override
    public int[] literals() {
        return literals.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Constraint c && reason.equals(c.reason) && Arrays.equals(literals, c.literals);
    }

    @Override
    public int hashCode() {
        return 31 * reason.hashCode() + Arrays.hashCode(literals);
    }

    @Override
    public String toString() {
        return Arrays.toString(literals) + " (" + reason + ")";
    }
}
