# Blossom Pairing Strategy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `BlossomPairingStrategy` that produces a minimum-cost player matching via Edmonds' Blossom algorithm, selectable with `pairing.strategy=blossom`, where cost = past-match count plus a two-tier recency penalty.

**Architecture:** A self-contained `PairingStrategy` implementation builds a complete weighted graph (`SimpleWeightedGraph`) over the players, pads it with cost-0 dummy vertices to absorb byes and unequal bipartite sizes, and solves it with JGraphT's `KolmogorovWeightedPerfectMatching`. Edge weight is `pastCount * 1000 + recencyPenalty`, where a within-last-week rematch (penalty 1500) sits above one count level and older brackets (15, 5) sit below it as tie-breakers. Wired into Spring via a `@ConditionalOnProperty` bean alongside the existing `least-played` (default) and `random` strategies.

**Tech Stack:** Java 21, Spring Boot 4.0.4, JGraphT 1.5.2 (`jgrapht-core`), JUnit 5 + Mockito + AssertJ, Maven.

**Spec:** `docs/superpowers/specs/2026-05-29-blossom-pairing-strategy-design.md`

**Git note:** The repository owner runs all git operations. Each task ends with a suggested commit command for **the user** to run — do not execute git yourself.

**Build note:** Use the system Maven (`mvn`) — the Maven wrapper cannot download in this environment. Run a single test class with `mvn -Dtest=BlossomPairingStrategyTest test`.

---

## File Structure

| File | Responsibility |
|---|---|
| `pom.xml` (modify) | Add the `jgrapht-core` dependency. |
| `src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java` (create) | The strategy: graph construction, weight function, dummy padding, matching translation. Self-contained — owns its repository access, weight logic, and private `PairKey`/`PairStats` records. |
| `src/main/java/cz/kula/killteamdiscordbot/pairing/PairingConfiguration.java` (modify) | Add the `blossom` bean definition. |
| `src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java` (create) | Unit tests with a mocked `PairingRepository`, mirroring `LeastPlayedPairingStrategyTest`. |

No database, schema, repository, or `Pairing` entity changes (spec non-goal). `PairingStrategy` interface and `PairingService` flow are untouched.

---

## Task 1: Add the JGraphT dependency

**Files:**
- Modify: `pom.xml` (dependencies section, after the `jspecify` dependency ending at line 55)

- [ ] **Step 1: Add the dependency**

In `pom.xml`, insert this block immediately after the closing `</dependency>` of the `jspecify` dependency (the one with `<artifactId>jspecify</artifactId>`), before the blank line preceding `spring-boot-docker-compose`:

```xml
        <dependency>
            <groupId>org.jgrapht</groupId>
            <artifactId>jgrapht-core</artifactId>
            <version>1.5.2</version>
        </dependency>
```

- [ ] **Step 2: Verify it resolves and the project still compiles**

Run: `mvn -q -DskipTests compile`
Expected: `BUILD SUCCESS`. Maven downloads `jgrapht-core` and its transitive `org.jheaps:jheaps`. No compilation errors.

- [ ] **Step 3: Commit (user runs)**

```bash
git add pom.xml
git commit -m "build: add jgrapht-core dependency for Blossom matching"
```

---

## Task 2: Create the strategy with trivial general cases

Establish the class implementing `PairingStrategy`. Handle the `generatePairings` short-circuits (0, 1, 2 players) that need no repository access or graph. The `>= 3` path and `generateBipartitePairings` throw `UnsupportedOperationException` for now — filled in by later tasks so the class compiles against the two-method interface.

**Files:**
- Create: `src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java`
- Create: `src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java`

- [ ] **Step 1: Write the failing trivial tests**

Create `src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java`:

```java
package cz.kula.killteamdiscordbot.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BlossomPairingStrategyTest {

    @Mock
    private PairingRepository pairingRepository;

    @InjectMocks
    private BlossomPairingStrategy strategy;

    @Test
    void emptyListProducesNoPairings() {
        assertThat(strategy.generatePairings(List.of())).isEmpty();
    }

    @Test
    void onePlayerProducesOneBye() {
        var result = strategy.generatePairings(List.of("a"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().player1DiscordUserId()).isEqualTo("a");
        assertThat(result.getFirst().player2DiscordUserId()).isNull();
    }

    @Test
    void twoPlayersArePairedWithoutTouchingRepository() {
        var result = strategy.generatePairings(List.of("a", "b"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().player1DiscordUserId()).isEqualTo("a");
        assertThat(result.getFirst().player2DiscordUserId()).isEqualTo("b");
    }

    // ---- helpers used by later tasks ----

    private Set<Set<String>> pairSet(List<PairingResult> results) {
        return results.stream()
                .filter(r -> r.player2DiscordUserId() != null)
                .map(r -> Set.of(r.player1DiscordUserId(), r.player2DiscordUserId()))
                .collect(Collectors.toSet());
    }

    private List<String> byes(List<PairingResult> results) {
        return results.stream()
                .filter(r -> r.player2DiscordUserId() == null)
                .map(PairingResult::player1DiscordUserId)
                .toList();
    }

    private void stubPast(List<Pairing> past) {
        when(pairingRepository.findByPlayerIds(anyList())).thenReturn(past);
    }

    private Pairing pairing(String p1, String p2, int daysAgo) {
        return Pairing.builder()
                .player1DiscordUserId(p1)
                .player2DiscordUserId(p2)
                .createdAt(OffsetDateTime.now().minusDays(daysAgo))
                .build();
    }
}
```

Note: the two-player test deliberately does **not** stub `findByPlayerIds`. With `MockitoExtension` strict stubbing, that confirms the `<= 2` path never touches the repository. The `pairSet`, `byes`, `stubPast`, and `pairing` helpers are unused for now — later tasks use them. (Mockito does not flag unused private helpers; only unused stubbings.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: FAIL — compilation error, `BlossomPairingStrategy` does not exist.

- [ ] **Step 3: Create the strategy class**

Create `src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java`:

```java
package cz.kula.killteamdiscordbot.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class BlossomPairingStrategy implements PairingStrategy {

    private final PairingRepository pairingRepository;

    @Override
    public List<PairingResult> generatePairings(List<String> discordUserIds) {
        log.info("BlossomPairingStrategy#generatePairings({})", discordUserIds);

        if (discordUserIds.isEmpty()) {
            return List.of();
        }
        if (discordUserIds.size() == 1) {
            return List.of(new PairingResult(discordUserIds.getFirst(), null));
        }
        if (discordUserIds.size() == 2) {
            return List.of(new PairingResult(discordUserIds.get(0), discordUserIds.get(1)));
        }

        throw new UnsupportedOperationException("general matching implemented in a later task");
    }

    @Override
    public BipartiteMatchResult generateBipartitePairings(List<String> groupA, List<String> groupB) {
        log.info("BlossomPairingStrategy#generateBipartitePairings({}, {})", groupA, groupB);
        throw new UnsupportedOperationException("bipartite matching implemented in a later task");
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: PASS — 3 tests green.

- [ ] **Step 5: Commit (user runs)**

```bash
git add src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java
git commit -m "feat: BlossomPairingStrategy skeleton with trivial general cases"
```

---

## Task 3: General matching by count (graph + dummy bye)

Implement the `>= 3` path: build a complete weighted graph, pad odd counts with one cost-0 dummy bye vertex, solve with `KolmogorovWeightedPerfectMatching`, translate the result. Weight is **count-only** for now (`recencyPenalty` returns 0); recency brackets arrive in Task 4. Past pairings in these tests are aged 100 days so recency would be 0 anyway — isolating count behavior.

**Files:**
- Modify: `src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java`
- Modify: `src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java`

- [ ] **Step 1: Write the failing tests**

Add these methods to `BlossomPairingStrategyTest` (before the helpers):

```java
    @Test
    void choosesOptimalMatchingWhereGreedyWouldFail() {
        // Greedy sorts pairs by count: it grabs a-b(0) first, forcing the expensive c-d(5).
        // Greedy total = 5. Optimal avoids both a-b and c-d: a-c(1)+b-d(1) = 2.
        stubPast(List.of(
                pairing("c", "d", 100), pairing("c", "d", 100), pairing("c", "d", 100),
                pairing("c", "d", 100), pairing("c", "d", 100),
                pairing("a", "c", 100),
                pairing("b", "d", 100),
                pairing("a", "d", 100),
                pairing("b", "c", 100)
        ));

        var result = strategy.generatePairings(List.of("a", "b", "c", "d"));

        assertThat(result).hasSize(2);
        assertThat(byes(result)).isEmpty();
        // Both optimal matchings exclude the cheap-but-trapping a-b and the expensive c-d.
        assertThat(pairSet(result)).doesNotContain(Set.of("a", "b"), Set.of("c", "d"));
    }

    @Test
    void oddCountGivesByeToThePlayerWhoseExclusionIsCheapest() {
        // e has played everyone 5 times; a,b,c,d have never played each other.
        // Cheapest overall: exclude e (bye), pair a-b, c-d at cost 0.
        stubPast(List.of(
                pairing("e", "a", 100), pairing("e", "a", 100), pairing("e", "a", 100),
                pairing("e", "a", 100), pairing("e", "a", 100),
                pairing("e", "b", 100), pairing("e", "b", 100), pairing("e", "b", 100),
                pairing("e", "b", 100), pairing("e", "b", 100),
                pairing("e", "c", 100), pairing("e", "c", 100), pairing("e", "c", 100),
                pairing("e", "c", 100), pairing("e", "c", 100),
                pairing("e", "d", 100), pairing("e", "d", 100), pairing("e", "d", 100),
                pairing("e", "d", 100), pairing("e", "d", 100)
        ));

        var result = strategy.generatePairings(List.of("a", "b", "c", "d", "e"));

        assertThat(result).hasSize(3);
        assertThat(byes(result)).containsExactly("e");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: FAIL — both new tests throw `UnsupportedOperationException` from the `>= 3` path.

- [ ] **Step 3: Implement the general matching**

Replace the entire contents of `BlossomPairingStrategy.java` with:

```java
package cz.kula.killteamdiscordbot.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class BlossomPairingStrategy implements PairingStrategy {

    private static final int COUNT_WEIGHT = 1000;
    private static final String BYE_TOKEN = "__BYE__";

    private final PairingRepository pairingRepository;

    @Override
    public List<PairingResult> generatePairings(List<String> discordUserIds) {
        log.info("BlossomPairingStrategy#generatePairings({})", discordUserIds);

        if (discordUserIds.isEmpty()) {
            return List.of();
        }
        if (discordUserIds.size() == 1) {
            return List.of(new PairingResult(discordUserIds.getFirst(), null));
        }
        if (discordUserIds.size() == 2) {
            return List.of(new PairingResult(discordUserIds.get(0), discordUserIds.get(1)));
        }

        Map<PairKey, PairStats> stats = buildPerPairStats(discordUserIds);
        OffsetDateTime now = OffsetDateTime.now();

        List<String> nodes = new ArrayList<>(discordUserIds);
        if (nodes.size() % 2 != 0) {
            nodes.add(BYE_TOKEN);
        }

        var graph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        for (String node : nodes) {
            graph.addVertex(node);
        }
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                String a = nodes.get(i);
                String b = nodes.get(j);
                DefaultWeightedEdge edge = graph.addEdge(a, b);
                double weight = (isDummy(a) || isDummy(b)) ? 0.0 : computeWeight(a, b, stats, now);
                graph.setEdgeWeight(edge, weight);
            }
        }

        var matching = new KolmogorovWeightedPerfectMatching<>(graph).getMatching();

        var results = new ArrayList<PairingResult>();
        for (DefaultWeightedEdge edge : matching.getEdges()) {
            String a = graph.getEdgeSource(edge);
            String b = graph.getEdgeTarget(edge);
            if (isDummy(a)) {
                results.add(new PairingResult(b, null));
            } else if (isDummy(b)) {
                results.add(new PairingResult(a, null));
            } else {
                results.add(new PairingResult(a, b));
            }
        }
        return results;
    }

    @Override
    public BipartiteMatchResult generateBipartitePairings(List<String> groupA, List<String> groupB) {
        log.info("BlossomPairingStrategy#generateBipartitePairings({}, {})", groupA, groupB);
        throw new UnsupportedOperationException("bipartite matching implemented in a later task");
    }

    private Map<PairKey, PairStats> buildPerPairStats(List<String> playerIds) {
        Set<String> playerSet = new HashSet<>(playerIds);
        Map<PairKey, PairStats> stats = new HashMap<>();
        for (Pairing pairing : pairingRepository.findByPlayerIds(playerIds)) {
            String p1 = pairing.getPlayer1DiscordUserId();
            String p2 = pairing.getPlayer2DiscordUserId();
            if (p2 == null || !playerSet.contains(p1) || !playerSet.contains(p2)) {
                continue;
            }
            PairKey key = new PairKey(p1, p2);
            stats.merge(
                    key,
                    new PairStats(1, pairing.getCreatedAt()),
                    (existing, incoming) -> new PairStats(
                            existing.count() + incoming.count(),
                            incoming.lastPlayedAt().isAfter(existing.lastPlayedAt())
                                    ? incoming.lastPlayedAt()
                                    : existing.lastPlayedAt()));
        }
        return stats;
    }

    private double computeWeight(String a, String b, Map<PairKey, PairStats> stats, OffsetDateTime now) {
        PairStats s = stats.get(new PairKey(a, b));
        if (s == null) {
            return 0.0;
        }
        return (double) s.count() * COUNT_WEIGHT + recencyPenalty(s.lastPlayedAt(), now);
    }

    private int recencyPenalty(OffsetDateTime lastPlayedAt, OffsetDateTime now) {
        return 0; // recency brackets added in Task 4
    }

    private boolean isDummy(String node) {
        return node.startsWith("__BYE");
    }

    private record PairKey(String a, String b) {
        private PairKey(String a, String b) {
            if (a.compareTo(b) <= 0) {
                this.a = a;
                this.b = b;
            } else {
                this.a = b;
                this.b = a;
            }
        }
    }

    private record PairStats(int count, OffsetDateTime lastPlayedAt) {
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: PASS — 5 tests green (3 trivial + 2 new).

- [ ] **Step 5: Commit (user runs)**

```bash
git add src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java
git commit -m "feat: optimal count-based general matching via Blossom"
```

---

## Task 4: Two-tier recency brackets

Add the recency penalty so the weight becomes `count * 1000 + recencyPenalty`. Brackets: `<= 8d -> 1500` (above the count tier), `<= 15d -> 15`, `<= 36d -> 5`, else `0`. Three tests pin the behavior: the mid bracket as a tie-breaker, the last-week bracket overriding a +1 count increase, and the guard that a +2 count saving still wins.

**Files:**
- Modify: `src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java`
- Modify: `src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `BlossomPairingStrategyTest`:

```java
    @Test
    void recencyBreaksTiesWithinEqualCountTier() {
        // Every pair has count 1, so all three matchings tie on count (total 2).
        // Only a-b was played recently (10 days -> mid bracket 15). Optimal avoids a-b.
        stubPast(List.of(
                pairing("a", "b", 10),
                pairing("c", "d", 100),
                pairing("a", "c", 100),
                pairing("b", "d", 100),
                pairing("a", "d", 100),
                pairing("b", "c", 100)
        ));

        var result = strategy.generatePairings(List.of("a", "b", "c", "d"));

        assertThat(result).hasSize(2);
        assertThat(pairSet(result)).doesNotContain(Set.of("a", "b"));
    }

    @Test
    void lastWeekRematchIsAvoidedAtPlusOneCount() {
        // Lowest-count matching {a-b, c-d} = count 2 but a-b was played 3 days ago (1500).
        //   weight = (1000+1500) + 1000 = 3500
        // Alternative {a-c, b-d} = count 3 (+1), no recent rematch:
        //   weight = 1000 + 2000 = 3000  -> wins.
        stubPast(List.of(
                pairing("a", "b", 3),
                pairing("c", "d", 100),
                pairing("a", "c", 100),
                pairing("b", "d", 100), pairing("b", "d", 100),
                pairing("a", "d", 100), pairing("a", "d", 100),
                pairing("b", "c", 100), pairing("b", "c", 100)
        ));

        var result = strategy.generatePairings(List.of("a", "b", "c", "d"));

        assertThat(pairSet(result)).containsExactlyInAnyOrder(Set.of("a", "c"), Set.of("b", "d"));
    }

    @Test
    void lastWeekRematchIsKeptWhenAvoidingItCostsPlusTwoCount() {
        // {a-b, c-d} = count 2, a-b played 3 days ago: weight (1000+1500)+1000 = 3500.
        // Both alternatives cost count 4 (+2): weight 2000+2000 = 4000. Rematch is kept.
        stubPast(List.of(
                pairing("a", "b", 3),
                pairing("c", "d", 100),
                pairing("a", "c", 100), pairing("a", "c", 100),
                pairing("b", "d", 100), pairing("b", "d", 100),
                pairing("a", "d", 100), pairing("a", "d", 100),
                pairing("b", "c", 100), pairing("b", "c", 100)
        ));

        var result = strategy.generatePairings(List.of("a", "b", "c", "d"));

        assertThat(pairSet(result)).containsExactlyInAnyOrder(Set.of("a", "b"), Set.of("c", "d"));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: FAIL. `lastWeekRematchIsAvoidedAtPlusOneCount` fails (with recency 0 the count-2 matching keeps a-b). `recencyBreaksTiesWithinEqualCountTier` fails or is flaky (with recency 0 all matchings tie and a-b may be picked). `lastWeekRematchIsKeptWhenAvoidingItCostsPlusTwoCount` already passes by count alone — that's fine, it stays green after the change too.

- [ ] **Step 3: Implement the recency brackets**

In `BlossomPairingStrategy.java`, add the `ChronoUnit` import alongside the existing `java.time.OffsetDateTime` import:

```java
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
```

Then replace the placeholder `recencyPenalty` method:

```java
    private int recencyPenalty(OffsetDateTime lastPlayedAt, OffsetDateTime now) {
        return 0; // recency brackets added in Task 4
    }
```

with:

```java
    private int recencyPenalty(OffsetDateTime lastPlayedAt, OffsetDateTime now) {
        long daysSince = ChronoUnit.DAYS.between(lastPlayedAt, now);
        if (daysSince <= 8) {
            return 1500; // played in the last week — above the count tier
        }
        if (daysSince <= 15) {
            return 15;
        }
        if (daysSince <= 36) {
            return 5;
        }
        return 0;
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: PASS — 8 tests green.

- [ ] **Step 5: Commit (user runs)**

```bash
git add src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java
git commit -m "feat: two-tier recency penalty for Blossom matching"
```

---

## Task 5: Bipartite matching (learners x teachers)

Implement `generateBipartitePairings`: empty-side guards, pad the smaller group with cost-0 dummies, build a complete bipartite weighted graph, solve, and translate — real-real edges become `PairingResult(learner, teacher)` (group A side first), dummy edges mark the real player as `unmatched`.

**Files:**
- Modify: `src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java`
- Modify: `src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java`

- [ ] **Step 1: Write the failing tests**

Add to `BlossomPairingStrategyTest`:

```java
    @Test
    void bipartiteBothSidesEmptyProducesNothing() {
        var result = strategy.generateBipartitePairings(List.of(), List.of());

        assertThat(result.pairings()).isEmpty();
        assertThat(result.unmatched()).isEmpty();
    }

    @Test
    void bipartiteOneSideEmptyReturnsOtherSideUnmatched() {
        var result = strategy.generateBipartitePairings(List.of(), List.of("ta", "tb"));

        assertThat(result.pairings()).isEmpty();
        assertThat(result.unmatched()).containsExactlyInAnyOrder("ta", "tb");
    }

    @Test
    void bipartiteEqualSizesAssignsMinimumCost() {
        // Diagonal is free (count 0), everything else costs 5. Optimum is the diagonal.
        stubPast(List.of(
                pairing("la", "tb", 100), pairing("la", "tb", 100), pairing("la", "tb", 100),
                pairing("la", "tb", 100), pairing("la", "tb", 100),
                pairing("la", "tc", 100), pairing("la", "tc", 100), pairing("la", "tc", 100),
                pairing("la", "tc", 100), pairing("la", "tc", 100),
                pairing("lb", "ta", 100), pairing("lb", "ta", 100), pairing("lb", "ta", 100),
                pairing("lb", "ta", 100), pairing("lb", "ta", 100),
                pairing("lb", "tc", 100), pairing("lb", "tc", 100), pairing("lb", "tc", 100),
                pairing("lb", "tc", 100), pairing("lb", "tc", 100),
                pairing("lc", "ta", 100), pairing("lc", "ta", 100), pairing("lc", "ta", 100),
                pairing("lc", "ta", 100), pairing("lc", "ta", 100),
                pairing("lc", "tb", 100), pairing("lc", "tb", 100), pairing("lc", "tb", 100),
                pairing("lc", "tb", 100), pairing("lc", "tb", 100)
        ));

        var result = strategy.generateBipartitePairings(List.of("la", "lb", "lc"), List.of("ta", "tb", "tc"));

        assertThat(result.unmatched()).isEmpty();
        assertThat(result.pairings()).containsExactlyInAnyOrder(
                new PairingResult("la", "ta"),
                new PairingResult("lb", "tb"),
                new PairingResult("lc", "tc"));
    }

    @Test
    void bipartiteMoreLearnersThanTeachersLeavesCheapestExcludedUnmatched() {
        // 4 learners, 2 teachers. la-ta and lb-tb are free; lc and ld cost 5 against everyone.
        // Optimum pairs la-ta, lb-tb and leaves lc, ld unmatched.
        stubPast(List.of(
                pairing("la", "tb", 100), pairing("la", "tb", 100), pairing("la", "tb", 100),
                pairing("la", "tb", 100), pairing("la", "tb", 100),
                pairing("lb", "ta", 100), pairing("lb", "ta", 100), pairing("lb", "ta", 100),
                pairing("lb", "ta", 100), pairing("lb", "ta", 100),
                pairing("lc", "ta", 100), pairing("lc", "ta", 100), pairing("lc", "ta", 100),
                pairing("lc", "ta", 100), pairing("lc", "ta", 100),
                pairing("lc", "tb", 100), pairing("lc", "tb", 100), pairing("lc", "tb", 100),
                pairing("lc", "tb", 100), pairing("lc", "tb", 100),
                pairing("ld", "ta", 100), pairing("ld", "ta", 100), pairing("ld", "ta", 100),
                pairing("ld", "ta", 100), pairing("ld", "ta", 100),
                pairing("ld", "tb", 100), pairing("ld", "tb", 100), pairing("ld", "tb", 100),
                pairing("ld", "tb", 100), pairing("ld", "tb", 100)
        ));

        var result = strategy.generateBipartitePairings(
                List.of("la", "lb", "lc", "ld"), List.of("ta", "tb"));

        assertThat(result.pairings()).containsExactlyInAnyOrder(
                new PairingResult("la", "ta"),
                new PairingResult("lb", "tb"));
        assertThat(result.unmatched()).containsExactlyInAnyOrder("lc", "ld");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: FAIL — the two non-empty bipartite tests throw `UnsupportedOperationException`. (The two empty-side tests would pass once the guard exists, but the method still throws today, so all four fail to compile-run against the stub — they fail.)

- [ ] **Step 3: Implement bipartite matching**

In `BlossomPairingStrategy.java`, add this import alongside the other `java.util` imports:

```java
import java.util.stream.Stream;
```

Replace the placeholder `generateBipartitePairings` method:

```java
    @Override
    public BipartiteMatchResult generateBipartitePairings(List<String> groupA, List<String> groupB) {
        log.info("BlossomPairingStrategy#generateBipartitePairings({}, {})", groupA, groupB);
        throw new UnsupportedOperationException("bipartite matching implemented in a later task");
    }
```

with:

```java
    @Override
    public BipartiteMatchResult generateBipartitePairings(List<String> groupA, List<String> groupB) {
        log.info("BlossomPairingStrategy#generateBipartitePairings({}, {})", groupA, groupB);

        if (groupA.isEmpty() || groupB.isEmpty()) {
            var unmatched = Stream.concat(groupA.stream(), groupB.stream()).toList();
            return new BipartiteMatchResult(List.of(), unmatched);
        }

        List<String> allPlayers = Stream.concat(groupA.stream(), groupB.stream()).toList();
        Map<PairKey, PairStats> stats = buildPerPairStats(allPlayers);
        OffsetDateTime now = OffsetDateTime.now();

        List<String> paddedA = new ArrayList<>(groupA);
        List<String> paddedB = new ArrayList<>(groupB);
        int diff = groupA.size() - groupB.size();
        if (diff > 0) {
            for (int i = 0; i < diff; i++) {
                paddedB.add("__BYE_B_" + i + "__");
            }
        } else if (diff < 0) {
            for (int i = 0; i < -diff; i++) {
                paddedA.add("__BYE_A_" + i + "__");
            }
        }

        var graph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        for (String a : paddedA) {
            graph.addVertex(a);
        }
        for (String b : paddedB) {
            graph.addVertex(b);
        }
        for (String a : paddedA) {
            for (String b : paddedB) {
                DefaultWeightedEdge edge = graph.addEdge(a, b);
                double weight = (isDummy(a) || isDummy(b)) ? 0.0 : computeWeight(a, b, stats, now);
                graph.setEdgeWeight(edge, weight);
            }
        }

        var matching = new KolmogorovWeightedPerfectMatching<>(graph).getMatching();

        Set<String> aSide = new HashSet<>(paddedA);
        var pairings = new ArrayList<PairingResult>();
        var unmatched = new ArrayList<String>();
        for (DefaultWeightedEdge edge : matching.getEdges()) {
            String source = graph.getEdgeSource(edge);
            String target = graph.getEdgeTarget(edge);
            String learner = aSide.contains(source) ? source : target;
            String teacher = learner.equals(source) ? target : source;

            if (isDummy(learner) && isDummy(teacher)) {
                continue;
            } else if (isDummy(teacher)) {
                unmatched.add(learner);
            } else if (isDummy(learner)) {
                unmatched.add(teacher);
            } else {
                pairings.add(new PairingResult(learner, teacher));
            }
        }
        return new BipartiteMatchResult(pairings, unmatched);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -Dtest=BlossomPairingStrategyTest test`
Expected: PASS — 12 tests green.

- [ ] **Step 5: Commit (user runs)**

```bash
git add src/main/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategy.java src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java
git commit -m "feat: bipartite Blossom matching with dummy padding"
```

---

## Task 6: Wire the strategy into Spring

Register the `blossom` bean so `pairing.strategy=blossom` selects it. `least-played` stays the `matchIfMissing` default.

**Files:**
- Modify: `src/main/java/cz/kula/killteamdiscordbot/pairing/PairingConfiguration.java`

- [ ] **Step 1: Add the bean**

In `PairingConfiguration.java`, add this bean method after the `leastPlayedPairingStrategy` method (before the closing brace of the class):

```java
    @Bean
    @ConditionalOnProperty(name = "pairing.strategy", havingValue = "blossom")
    PairingStrategy blossomPairingStrategy(PairingRepository pairingRepository) {
        return new BlossomPairingStrategy(pairingRepository);
    }
```

The existing imports (`ConditionalOnProperty`, `Bean`, `Configuration`) already cover this — no new imports needed.

- [ ] **Step 2: Verify the full build and entire test suite**

Run: `mvn clean verify`
Expected: `BUILD SUCCESS`. All tests pass (the existing 6 plus the 12 new Blossom unit tests), jar packaged. Spring Modulith verification still passes — `BlossomPairingStrategy` lives in the existing `pairing` module and introduces no new cross-module dependency.

- [ ] **Step 3: Commit (user runs)**

```bash
git add src/main/java/cz/kula/killteamdiscordbot/pairing/PairingConfiguration.java
git commit -m "feat: register blossom pairing strategy bean"
```

---

## Self-Review (completed during planning)

**Spec coverage:**
- `BlossomPairingStrategy` selectable via `pairing.strategy=blossom` → Task 6.
- Minimum-cost matching / greedy-beats-optimal → Task 3 + its test.
- Weight = `count * COUNT_WEIGHT + recencyPenalty`, two-tier brackets (1500 / 15 / 5 / 0) → Task 4.
- Odd player count → dummy bye → Task 3 (`oddCount...` test).
- Unequal bipartite sizes → dummy padding, leftover unmatched → Task 5 (`bipartiteMoreLearners...` test).
- Bipartite empty-side guards → Task 5.
- 0 / 1 / 2-player short-circuits → Task 2.
- JGraphT `jgrapht-core` 1.5.2 dependency → Task 1.
- No interface / `PairingService` / schema / repository changes → honored (only the four files in File Structure are touched).
- Test list items 1–7 from the spec → covered by the 12 unit tests across Tasks 2–5. (Spec test 4b "last-week overrides +1 / yields to +2" → both `lastWeekRematch...` tests in Task 4.)

**Placeholder scan:** No TBD/TODO/"handle edge cases" — every step has concrete code or an exact command.

**Type consistency:** `PairKey`/`PairStats` records, `buildPerPairStats`, `computeWeight`, `recencyPenalty`, `isDummy`, `BYE_TOKEN`, `COUNT_WEIGHT` are defined in Task 3 and reused unchanged in Tasks 4–5. `recencyPenalty` signature is identical between its Task 3 placeholder and Task 4 implementation. `KolmogorovWeightedPerfectMatching` (default `MINIMIZE` objective), `SimpleWeightedGraph`, `DefaultWeightedEdge` used consistently. `PairingResult(learner, teacher)` ordering matches the existing `LeastPlayedPairingStrategy` convention and `PairingService` expectations.

**Note for the implementer:** `KolmogorovWeightedPerfectMatching`'s single-argument constructor computes a *minimum*-weight perfect matching by default — that is what we want. It requires an even vertex count and that a perfect matching exists; the dummy padding (general) and equal-size padding (bipartite), combined with complete graphs, guarantee both.
