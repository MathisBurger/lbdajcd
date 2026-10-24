# Encoding: Java code → feature model → CNF

Status: draft, 2026-10-24

**Prototype scope (frozen 2026-10-21).** This document specifies the full
encoding. The prototype implements only part of it:

| Part | Status |
|---|---|
| Encoding and queries Q1–Q5 | implemented; checked on the hand-built model of Example A (`ExampleATest`) |
| Extractor layer 1: feature tree | implemented (`ExtractorTreeTest`) |
| Extractor layer 2: plain references (inheritance, type usage, annotations, field access, constructor calls) | implemented (`ExtractorReferenceTest`) |
| Extractor layer 3: method calls, implicit `super()` | specified, not implemented |
| Extractor layer 4: polymorphism (or-groups, implementation obligation, lambdas as implementations) | specified, not implemented |
| Extractor layer 5: roots | specified, not implemented |
| End to end on Example A with layers 1–2 | runs; answers checked against hand-derived ones, several of them wrong because layers 3–5 are missing (`EndToEndTest`, Section 5) |
| Propagation baseline, evaluation on a real subject | not implemented / not run |

Consequence: the complete model of Example A exists only as a hand-built
model. From source, the extractor produces a partial model; the queries
run end to end on it, but answer correctly only where roots and
polymorphism are not needed. The remaining parts belong to the actual
research.

This document defines how a Java project is turned into a feature model,
how the feature model is turned into CNF, and which SAT/MaxSAT queries
answer the questions an AI assistant might ask before deleting code:

> Can X be deleted? If not, which functionality breaks, and why?

## 1. Features (Boolean variables)

Every feature is one Boolean variable. `true` means "present in the code",
`false` means "deleted".

The proposal is to use
[iluwatar/java-design-patterns](https://github.com/iluwatar/java-design-patterns)
for the evaluation in the research: it covers the common Java design
patterns and is therefore representative of how real Java projects are
structured. Larger open-source projects such as Apache Kafka can be
analysed as well, but evaluating the correctness of the answers there
requires in-depth knowledge of the codebase.

| Feature | Parent | Example identifier |
|---|---|---|
| Project root | — | `ROOT` |
| Package | parent package or `ROOT` | `com.iluwatar.strategy` |
| Class / interface / enum / record (top-level) | its package | `com.iluwatar.strategy.DragonSlayer` |
| Nested (named) class | its enclosing class | `…LambdaStrategy$Strategy` |
| Field (incl. enum constant) | its class | `…DragonSlayer#strategy` |
| Constructor (one feature per overload) | its class | `…DragonSlayer#<init>(DragonSlayingStrategy)` |
| Method (one feature per overload) | its class | `…DragonSlayer#changeStrategy(DragonSlayingStrategy)` |

- Overloads are distinguished by their parameter types (erasure), so
  `bar(int)` and `bar(long)` are two features. Parameter names are not
  part of the identifier: they do not affect compilation.
- Parameters are **not** separate features (decided to keep the prototype
  simple). A method's parameter types are treated as type usages of the
  method itself (Section 2.2). Changing a parameter list = deleting one
  overload and adding another.
- Anonymous classes and lambdas are **not** features. Their references are
  attributed to the enclosing member (method, constructor or field
  initialiser).
- Only project code is modelled. JDK and library types are not features
  and are treated as always present.
- An implicit default constructor is not a feature; `new C()` on such a
  class requires the class `C`.

Implementation details of the extractor (layer 1, `Extractor`):

- Only packages that contain at least one source file are features. A
  package's parent is the longest such package that is a prefix of its
  name (`q.sub → q`), otherwise `ROOT`. Empty intermediate packages
  (`com`, `com.example`) are not modelled.
- Local classes (declared inside a method) are treated like anonymous
  classes: not features. Initializer blocks (`static { … }`) are not
  features either.
- Record components count as fields. Annotation-type elements count as
  methods.
- Signature format: parameter types as written in the source, without
  type arguments, comma-separated, varargs as arrays:
  `generic(List,T,String[])`. Signature types are not resolved, so this
  only approximates the erasure: a type variable stays `T`, and two
  classes with the same simple name are not told apart. This could be addressed in the research, for instance by identifying each
  parameter type by its resolved, fully qualified name.

Implementation details of the extractor (layer 2, plain references):

- Names are resolved with the JavaParser symbol solver against the JDK and
  the project sources. Library types (e.g. JUnit) cannot be resolved; they
  are reported as warnings and otherwise ignored.
- Annotations are type usages (`@Marker` → `Marker`).
- References in initializer blocks are attributed to the class.
- A clause `source → target` is skipped if `target` is `source` or one of
  its ancestors, since the tree clauses already imply it. Duplicate clauses
  are generated once.
- A constructor call requires the constructor the compiler resolves (or
  only the class, for an implicit default constructor).
- Creating an anonymous class (`new Api() { … }`) counts only as a type
  usage of `Api`; the superclass constructor it calls is not modelled.
- **Implicit `super()` (decided, not implemented):** a class whose
  constructors do not call `super(…)`/`this(…)` explicitly depends on its
  superclass's no-argument constructor. This is to be modelled as a
  constructor call from each such constructor (or from the class if it
  declares none). Until then, deleting that constructor breaks compilation
  without the model noticing.

A more focused research approach would analyse which existing Java design
patterns occur in practice and how relevant they are.

## 2. Constraints (clauses)

Notation: `a → b` is the clause `¬a ∨ b`; `a → (b ∨ c)` is `¬a ∨ b ∨ c`.
Every constraint below is already a single clause, so no Tseitin or
distributive CNF transformation is needed.

### 2.1 Tree constraints

| Constraint | Clause | Why |
|---|---|---|
| Project root present | `ROOT` | Feature-model convention. |
| Child requires parent | `child → parent` | A member cannot exist without its class, a class without its package. |

All other children (classes, fields, constructors, methods) are
**optional**: they are deletion candidates.

### 2.2 Reference constraints ("requires", cross-tree)

The *source* of a reference is the innermost feature that contains it:
a method, constructor or field (initialiser); for class-level references
(`extends`, `implements`) the class itself.

| Reference in code | Clause |
|---|---|
| Method / constructor call | `source → o` where `o` is the overload the compiler resolves (or the class, if `o` is implicit, e.g. a default constructor or an enum's `valueOf`) |
| Type usage (parameter, return, field, local variable, cast, class literal, generic argument) | `source → T`; for parameter and return types the source is the method/constructor |
| Inheritance (`extends` / `implements`) | `C → S` |
| Field access | `source → f` |

Binding a call to the resolved overload only is a deliberate
simplification. It causes false positives when another overload would take
over after a deletion: if `bar(int)` is deleted, `bar(5)` still compiles
against `bar(long)` (widening), but the model reports the call as broken.
Binding to all applicable overloads (`source → (o_1 ∨ … ∨ o_k)`) would fix
this at the cost of re-implementing Java's applicability rules; left to
future work.

### 2.3 Polymorphism (or-groups)

Applies to interfaces **and** abstract classes / overridable methods alike.

| Constraint | Clause |
|---|---|
| Type-level or-group: if type `T` is present, at least one implementation remains | `T → (C_1 ∨ … ∨ C_n ∨ L_1 ∨ … ∨ L_m)` |
| Call on a supertype method `m` needs the declaration and at least one implementation | `source → m` and `source → (impl_1 ∨ … ∨ impl_k)` |
| Implementation obligation: a concrete class must implement every abstract method it inherits | `C → C.m'` (where `m'` is `C`'s implementation of `m`) |

- `C_i` are the concrete classes implementing/extending `T`.
- `L_j` / `impl` include the **enclosing members of lambdas** whose target
  type is `T` (functional interfaces).
- `impl_1 … impl_k` are: `m` itself if it is concrete, all overriding
  methods in subtypes, and enclosing members of lambdas targeting `m`'s
  interface. If `m` is concrete, the disjunction is already satisfied by
  `m`, so the rule only has an effect for abstract/interface methods.
- **Empty groups are skipped**: if `T` has no implementation in the
  project (e.g. it is only mocked in tests), no or-group clause is
  generated, so `T` is not forced to be absent.

The or-group is a **cross-tree** constraint, not a tree group: in the
feature tree implementations stay children of their own packages.

### 2.4 Roots ("functionality")

Functionality is the set of roots that must stay valid:

- every `main` method, and
- every test class (a class containing at least one `@Test` /
  `@ParameterizedTest` method).

| Constraint | Clause |
|---|---|
| Root is present | `r` (unit clause) |
| Root class requires all its declared members | `r → member` for each declared member of `r` |

The second rule reflects that Java compiles a class as a whole, and
prevents "satisfying" a root by silently deleting one of its test
methods. For a `main` method the root class is the class declaring it.

### 2.5 Deletion

Deleting a set `D` of features (usually a single class or member) adds
the unit clauses `¬d` for every `d ∈ D`. Descendants of `d` are then
false automatically via the tree constraints.

## 3. Queries

Let `F` be all clauses of Section 2 **without** the root unit clauses,
`R` the set of roots, and `D` the deletion set.

| # | Question | Query | Answer |
|---|---|---|---|
| Q1 | Can `D` be deleted **alone**? | SAT(`F ∧ R ∧ ¬D ∧ A`), with assumptions `A` = every feature not in `D` and not a descendant of `D` is `true` | SAT: yes. UNSAT: no. Directly checkable: delete the code, compile, run tests. |
| Q2 | Can `D` be deleted **with cascade**? | SAT(`F ∧ R ∧ ¬D`) | SAT: yes, if further dependent code is removed too. |
| Q3 | Which further code must go with `D`? (MaxSAT A) | `F ∧ ¬D` hard; soft unit clause `x` (weight 1) for every other feature | The cascade: features false in the optimum. Roots may appear in it. |
| Q4 | Which functionality breaks? (MaxSAT B) | `F ∧ ¬D` hard; soft unit clause `r` for every root | Minimal set of roots that must break. |
| Q5 | Why? | Minimal unsatisfiable subset (MUS) of the UNSAT formula from Q2, with clauses grouped by the code reference that produced them | A chain such as `ReportTest → ReportTest.total() → new Circle() → ¬Circle`, rendered as text for the AI. |

Answer format for the AI: deletable alone (Q1), deletable with cascade
(Q2), cascade (Q3), broken roots (Q4), explanation (Q5).

## 4. Complexity and why SAT is still used

Every clause in Section 2 has **at most one negative literal**: tree
constraints `¬c ∨ p`, requires `¬a ∨ b_1 ∨ … ∨ b_k`, or-groups
`¬T ∨ C_1 ∨ … ∨ C_n`, roots `r`, deletions `¬d`. Such formulas are
*dual-Horn*.

Consequences (proof sketch, to be checked):

- Satisfiability of dual-Horn formulas is decidable in linear time.
- The set of models is closed under union: if `M_1` and `M_2` satisfy a
  clause `¬a ∨ b_1 ∨ … ∨ b_k` and `a` is true in `M_1 ∪ M_2`, then `a` is
  true in some `M_i`, which makes some `b_j` true in `M_i` and hence in the
  union. Therefore a unique maximal model exists.
- That maximal model can be computed by starting from "all true except
  `D`" and propagating falsity to a fixpoint. It answers Q2–Q4 at once:
  Q3's cascade is its complement, Q4's broken roots are the roots it
  sets false.

So for the current encoding a graph/propagation algorithm gives the same
answers as SAT/MaxSAT. The prototype uses Sat4j anyway because:

- it gives MUS-based explanations (Q5) out of the box (convenience only:
  in the dual-Horn fragment the same chain can be read from propagation);
- the planned ruleset (Section 6) adds exactly-one and excludes
  constraints, which leave the dual-Horn fragment, so efficient
  propagation is no longer guaranteed (exact complexity still to be
  determined);
- the propagation algorithm serves as a **baseline** in the evaluation:
  both must agree on every query, and solver time is compared.
- rules outside the dual-Horn fragment can be added as further clauses
  without changing the solver. Such constraints (e.g. `A ∧ B → ¬C`) are
  better handled by SAT than by a custom graph algorithm: propagation
  alone no longer finds the answer, so the graph algorithm would have to
  add the search that a SAT solver already provides. How well the SAT and especially the MaxSAT
  queries scale on real projects has still to be measured.

## 5. Worked example A (synthetic, complete CNF)

Intended as the first JUnit test.

```java
package p;

interface Shape { double area(); }

class Circle implements Shape { public double area() { return 1; } }

class Square implements Shape { public double area() { return 1; } }

class Report {
    static double total(Shape s) { return s.area(); }
}

class ReportTest {
    @Test void total() { Report.total(new Circle()); }
}
```

### Variables

| # | Feature | # | Feature |
|---|---|---|---|
| 1 | `ROOT` | 7 | `Square` |
| 2 | `p` | 8 | `Square#area()` |
| 3 | `Shape` | 9 | `Report` |
| 4 | `Shape#area()` | 10 | `Report#total(Shape)` |
| 5 | `Circle` | 11 | `ReportTest` (root) |
| 6 | `Circle#area()` | 12 | `ReportTest#total()` |

### Clauses (DIMACS literals)

| Group | Clauses |
|---|---|
| Project root | `1` |
| Tree: child → parent | `-2 1`, `-3 2`, `-5 2`, `-7 2`, `-9 2`, `-11 2`, `-4 3`, `-6 5`, `-8 7`, `-10 9`, `-12 11` |
| Inheritance | `-5 3`, `-7 3` |
| Implementation obligation | `-5 6`, `-7 8` |
| Type-level or-group | `-3 5 7` |
| Type usage (parameter type of `total`) | `-10 3` |
| Polymorphic call `s.area()` | `-10 4`, `-10 6 8` |
| Test: call `Report.total` | `-12 10` |
| Test: `new Circle()` (implicit constructor) | `-12 5` |
| Root | `11` |
| Root class requires members | `-11 12` |

24 clauses, 12 variables.

### Expected answers

| Deletion | Q1 alone | Q2 cascade | Q3 cascade set | Q4 broken roots | Q5 MUS |
|---|---|---|---|---|---|
| `Square` | yes | yes | `Square`, `Square#area()` | — | — |
| `Circle` | no | no | `Circle`, `Circle#area()`, `ReportTest#total()`, `ReportTest` | `ReportTest` | `11`, `-11 12`, `-12 5`, `-5` |
| `Square#area()` | no | yes | `Square#area()`, `Square` | — | (Q1 only: `Square` would lack `area()`) |

- `Square`: the or-group `-3 5 7` and the call `-10 6 8` are still
  satisfied by `Circle`. This is where the or-group matters.
- `Square#area()`: deleting only the method makes `Square` fail to compile
  (implementation obligation `-7 8`), so it is not deletable alone; with
  cascade, `Square` goes too.

Ground truth to confirm when the example exists as code: delete the
code, compile, run the test.

### End to end with extractor layers 1–2 only

Pipeline: sources in `src/test/resources/exampleA` → `Extractor` (layers 1
and 2) → `DeletionQueries`. The extracted model contains the 12 features,
the 11 tree clauses plus the project root, and the 4 plain references
(`Circle → Shape`, `Square → Shape`, `Report#total(Shape) → Shape`,
`ReportTest#total() → Circle`). It lacks the method calls, the or-group,
the implementation obligations and, above all, the roots.

Expected answers for this model, derived by hand:

| Deletion | Q1 alone | Q2 cascade | Q3 cascade set | Q4 broken roots | Same as full model? |
|---|---|---|---|---|---|
| `Square` | yes | yes | `Square`, `Square#area()` | — | yes |
| `Circle` | no | yes | `Circle`, `Circle#area()`, `ReportTest#total()` | — | Q1 only |
| `Square#area()` | yes | yes | `Square#area()` | — | Q2 and Q4 only |

- `Circle`, Q1 is still right: with everything else present,
  `ReportTest#total() → Circle` is violated. Without roots, nothing forces
  `ReportTest` to stay, so Q2 wrongly says yes, the cascade misses
  `ReportTest` (no "root class requires members" clause), no root is
  reported broken, and there is no MUS to explain.
- `Square#area()`: without the implementation obligation `Square →
  Square#area()`, the model does not see that `Square` would no longer
  compile, so Q1 wrongly says yes and Q3 misses `Square`.

This shows what layers 3–5 contribute: the roots (layer 5) make Q2, Q4 and
Q5 meaningful at all, and the implementation obligation (layer 4) is needed
even for Q1.

## 6. Limitations and future work

Not modelled in the prototype (future work):

- Reflection, `Class.forName`, `ServiceLoader`, dependency injection.
- References from resource files (XML, `META-INF/services`).
- Code generated at compile time (e.g. Lombok's `@Slf4j` creates a
  `LOGGER` field; references to it cannot be resolved from source).
- Runtime-created implementations (mocks, proxies). A test that only uses
  a Mockito mock of an interface still compiles and passes when every
  implementation is deleted, but the or-group reports it as broken
  (false positive).
- Behavioural breakage: code that still compiles but fails a test.
- **User ruleset** in UVL constraint syntax (e.g. restricting which
  implementations may be used). Constraint types to be decided. This is
  the step that leaves the dual-Horn fragment (Section 4).

Known weaknesses of the current identifiers and bindings:

- Signature types are taken as written, so two classes with the same
  simple name are not told apart and type variables are not erased
  (Section 1). Possible fix: identify each parameter type by its fully
  qualified, resolved name.
- Calls bind to the resolved overload only, which gives false positives
  when another overload would take over (Section 2.2).
- Implicit `super()` is not modelled yet, which gives false negatives
  (Section 1).

Open questions for the research:

- **Evaluation subject.** Proposed: `iluwatar/java-design-patterns`, since
  it covers the common Java design patterns and is therefore
  representative of how real Java code is structured. Larger open-source
  projects such as Apache Kafka are possible as well, but judging whether
  an answer is correct there requires in-depth knowledge of the codebase.
- **Focus on patterns.** A more focused approach would analyse which
  existing Java design patterns occur in practice, how relevant they are,
  and how each one is represented in the encoding.
- **SAT versus graph propagation.** SAT is favoured because it is easier
  to extend with assumptions, further queries and the ruleset than a
  hand-written graph algorithm. How well the SAT and MaxSAT queries scale
  on real projects has still to be measured against the propagation
  baseline (Section 4).

## 7. Decision log

| Decision | By |
|---|---|
| Feature tree package → class → overload; parameters not modelled as features for now (parameter types are type usages of the method) | author |
| Fields and constructors as children of their class; nested classes as children; anonymous classes and lambdas inline | author |
| Or-groups for interfaces and abstract classes; calls require the method and ≥ 1 implementation | author |
| Roots: `main` methods and test classes; root class requires all members | author |
| References: calls, type usage, inheritance, field access | author |
| Method and constructor calls bound to the resolved overload only (replaces the earlier "all applicable overloads") | author |
| Implicit `super()` to be modelled as a constructor call | author |
| Prototype frozen after extractor layer 2; layers 3–5, baseline and evaluation left to the research | author |
| Q1 (alone) and Q2 (with cascade) both reported; both MaxSAT queries | author |
| Keep SAT although the current fragment is dual-Horn; propagation as baseline; ruleset in UVL as future work | author |
| Implementation obligation, lambdas counted by enclosing member (incl. field initialisers), external types not modelled, empty groups skipped | author |
