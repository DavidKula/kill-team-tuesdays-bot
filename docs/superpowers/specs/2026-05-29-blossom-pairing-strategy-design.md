g# Blossom Pairing Strategy — Design

**Status:** Approved
**Date:** 2026-05-29
**Author:** David Kula

## Motivation

The existing `LeastPlayedPairingStrategy` uses a greedy approach: sort all candidate pairs by past-match count, then walk the sorted list, taking each pair if both players are still unmatched. Greedy is suboptimal — it can lock in cheap early pairs that force expensive late ones, producing a higher total repeat count than necessary.

We want an alternative strategy that finds the **provably minimum-cost matching** where cost = past pairings between two players. Edmonds' Blossom algorithm solves this in polynomial time for general (non-bipartite) graphs.

The new strategy coexists with the existing two; `least-played` remains the default.

## Goals

- Provide a `BlossomPairingStrategy` selectable via `pairing.strategy=blossom`.
- Minimize total past-match count across all pairings produced.
- Prefer pairs that have not played each other recently. A rematch within the last week is treated as a primary-tier cost that can override a slight (single-repeat) increase in total count; older recency differences only break ties within an equal-count tier.
- Handle odd player counts and unequal learner/teacher counts cleanly.
- No changes to the `PairingStrategy` interface or `PairingService` flow.

## Non-goals

- Does not replace `LeastPlayedPairingStrategy`. Both remain available.
- Does not introduce a shared "pairing history" helper. Duplication of the per-pair stats builder between strategies is acceptable for now; it can be unified later if a third weight-based strategy appears.
- No changes to the database schema, repository methods, or `Pairing` entity.
- No new integration test. Existing `LeastPlayedPairingStrategyIT` covers DB-loaded scenarios via the dump-based mechanism; a parallel Blossom IT can be added later if desired.

## Approach: dummy vertex padding

JGraphT's `KolmogorovWeightedPerfectMatching` requires every vertex to be matched. The codebase, however, supports byes (odd player count → one `PairingResult(p, null)`) and unequal bipartite groups (returned via `BipartiteMatchResult.unmatched`).

We bridge this gap by **padding the graph with cost-0 dummy vertices**:

- **Odd player count:** add one `DUMMY_BYE` vertex with cost-0 edges to every real player. The optimizer matches it to whichever player makes the remaining matching cheapest — equivalent to finding the optimal "n-exclusion" bye in a single algorithm run.
- **Unequal bipartite sizes** (`|A| ≠ |B|`): pad the smaller group with `||A| − |B||` dummies. Whoever matches with a dummy is returned as unmatched and falls through to the general pool, consistent with the existing `PairingService` flow.

Cost-0 dummy edges contribute nothing to the total, so the algorithm optimizes purely over real-edge cost.

## Class structure

### `BlossomPairingStrategy` (new)

Located in `cz.kula.killteamdiscordbot.pairing`. Implements `PairingStrategy`. Self-contained — owns its repository access and per-pair stats builder. Does not extend or share code with `LeastPlayedPairingStrategy`.

```java
@Slf4j
@RequiredArgsConstructor
public class BlossomPairingStrategy implements PairingStrategy {
    private final PairingRepository pairingRepository;

    @Override
    public List<PairingResult> generatePairings(List<String> discordUserIds) { ... }

    @Override
    public BipartiteMatchResult generateBipartitePairings(List<String> groupA, List<String> groupB) { ... }
}
```

### `PairingConfiguration` (modified)

Add one bean definition:

```java
@Bean
@ConditionalOnProperty(name = "pairing.strategy", havingValue = "blossom")
PairingStrategy blossomPairingStrategy(PairingRepository pairingRepository) {
    return new BlossomPairingStrategy(pairingRepository);
}
```

`least-played` remains the `matchIfMissing = true` default.

## Edge weight function

A single scalar weight encodes past-match count (the primary criterion) plus a recency term. Recency is **two-tiered**: a rematch within the last week carries a cost *above* one count level so it can override a slight count increase, while older recency differences sit *below* the count tier as pure tie-breakers.

```
weight(p1, p2) = pastCount(p1, p2) * COUNT_WEIGHT + recencyPenalty(p1, p2)
```

Constants:

- `COUNT_WEIGHT = 1000`

Recency penalty — **bracketed for weekly play**. Players play roughly once a week, so a continuous day-level ramp adds no signal; discrete week-ish brackets match the actual cadence:

```
recencyPenalty(p1, p2):
  if pair has never played:        0
  daysSince = whole days between now and lastPlayedAt
  if daysSince <= 8:   return 1500  // played in the last week — above the count tier
  if daysSince <= 15:  return 15    // ~2 weeks ago
  if daysSince <= 36:  return 5     // ~3–5 weeks ago
  return 0                           // > 36 days: long enough ago
```

Bracket boundaries are inclusive upper bounds (a game exactly 8 days out lands in the top bracket).

Behavior:

| Last played | Penalty | Tier |
|---|---|---|
| Within last week (≤ 8d) | 1500 | above count |
| ~2 weeks ago (9–15d) | 15 | tie-breaker |
| ~3–5 weeks ago (16–36d) | 5 | tie-breaker |
| > 36 days ago | 0 | — |
| Never played | 0 | — |

**How the two tiers interact:**

- *Last-week (1500):* worth 1.5 "virtual repeats." Avoiding a fresh rematch wins against a **+1** total-count increase but loses to **+2** (1000 < 1500 < 2000). Because 1500 is never an exact multiple of `COUNT_WEIGHT`, a *single* last-week rematch never ties a count difference — the decision is always unambiguous. The cost is additive per pair, so avoiding two last-week rematches is worth 3000 = exactly +3 count; that lone case lands on an exact tie at a +3 count swing and is resolved deterministically by the lower brackets. It is rare and benign.
- *Mid/low (15, 5):* pure tie-breakers. Max ~15 per pair, so they cannot sum past `COUNT_WEIGHT` until ~133 players — they only ever break ties within an equal-count tier.

Dummy bye edges always have weight 0.

## Algorithm flow

### `generatePairings(List<String> playerIds)`

```
1. if playerIds.size() == 0  → return []
2. if playerIds.size() == 1  → return [PairingResult(playerIds[0], null)]
3. if playerIds.size() == 2  → return [PairingResult(playerIds[0], playerIds[1])]   (short-circuit)
4. fetch past pairings:
     pastPairings = pairingRepository.findByPlayerIds(playerIds)
5. build perPairStats: Map<UnorderedPair<String>, (count, lastPlayedAt)>
     – iterate pastPairings; skip entries where player2 is null or either player is not in playerIds
     – count incremented; lastPlayedAt set to max(existing, pairing.createdAt)
6. nodes = new ArrayList<>(playerIds)
   if nodes.size() is odd: nodes += DUMMY_BYE_TOKEN
7. construct SimpleWeightedGraph<String, DefaultWeightedEdge>:
     for every unordered pair (a, b) in nodes:
       edge = graph.addEdge(a, b)
       if a or b is DUMMY_BYE_TOKEN → graph.setEdgeWeight(edge, 0)
       else                         → graph.setEdgeWeight(edge, computeWeight(a, b))
8. matching = new KolmogorovWeightedPerfectMatching<>(graph).getMatching()
9. translate each matched edge:
     edge (a, b) where neither is DUMMY_BYE_TOKEN → PairingResult(a, b)
     edge (player, DUMMY_BYE_TOKEN)               → PairingResult(player, null)
10. return list
```

### `generateBipartitePairings(List<String> groupA, List<String> groupB)`

```
1. if groupA.isEmpty() and groupB.isEmpty()  → BipartiteMatchResult([], [])
2. if either group is empty                  → BipartiteMatchResult([], all from non-empty group)
3. fetch past pairings spanning groupA ∪ groupB
4. build perPairStats as above
5. paddedA, paddedB = new ArrayLists
   diff = groupA.size() - groupB.size()
   if diff > 0: paddedB += diff dummies (DUMMY_BYE_1, DUMMY_BYE_2, ...)
   if diff < 0: paddedA += |diff| dummies
6. build SimpleWeightedGraph with edges only between paddedA and paddedB (no intra-group edges):
     for a in paddedA, b in paddedB:
       edge = graph.addEdge(a, b)
       if a or b is dummy → weight 0
       else               → weight = computeWeight(a, b)
7. matching = new KolmogorovWeightedPerfectMatching<>(graph).getMatching()
8. translate matching:
     real-real edge → PairingResult(learner, teacher)   (preserve groupA-side first per existing convention)
     dummy edge     → record the real player as unmatched
9. return BipartiteMatchResult(pairings, unmatched)
```

### Dummy token representation

DUMMY_BYE tokens are `String` values guaranteed not to clash with Discord user IDs — e.g. `"__BYE__"` for the general case, `"__BYE_A_<i>__"` / `"__BYE_B_<i>__"` for bipartite padding. Discord user IDs are numeric, so this is collision-free.

The `perPairStats` map uses an unordered key — at implementation time this is concretely a private record like `record PairKey(String a, String b)` whose constructor sorts the two strings so `(a,b)` and `(b,a)` produce equal keys, or equivalently a canonical `min+"|"+max` string. The design refers to it as `UnorderedPair` for readability.

## Edge cases

| Case | Behavior |
|---|---|
| 0 players (general) | empty list |
| 1 player (general) | single bye |
| 2 players (general) | trivial single pair, short-circuited before graph construction |
| Odd count ≥ 3 (general) | one DUMMY_BYE, one player gets the bye |
| Both bipartite sides empty | empty pairings, empty unmatched |
| One bipartite side empty | empty pairings, all players from non-empty side as unmatched |
| Bipartite equal sizes | pure perfect matching, no padding |
| Bipartite unequal sizes | larger side has `diff` players returned as unmatched |
| All players have played each other equally | algorithm picks any optimal matching; recency tie-breaker provides determinism in practice |

## Dependencies

Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.jgrapht</groupId>
    <artifactId>jgrapht-core</artifactId>
    <version>1.5.2</version>
</dependency>
```

(Pin to the latest stable 1.5.x at implementation time.)

JGraphT brings in `org.jheaps:jheaps` (~200 KB) as its only meaningful transitive dependency. Total footprint added: ~2 MB.

## Testing

New file: `src/test/java/cz/kula/killteamdiscordbot/pairing/BlossomPairingStrategyTest.java`.

Unit-test shape: mock `PairingRepository`, no Spring context, mirrors the existing `LeastPlayedPairingStrategyTest`. Tests:

1. **Trivial:**
   - 0 players → empty list
   - 1 player → `PairingResult(p, null)`
   - 2 players → single pair
2. **Greedy-beats-optimal case:** hand-crafted past pairings where greedy gives a strictly worse total than Blossom. Asserts new strategy returns the better matching. This is the key signal that the new strategy actually does what its name promises.
3. **Odd-count bye:** 5 players with known past counts; assert the specific player whose exclusion minimizes overall cost receives `PairingResult(p, null)`.
4. **Recency tie-breaker (below count):** 4 players where two candidate matchings tie on total count but one is strictly more recent (mid/low bracket) — assert the older-paired matching is chosen.
4b. **Last-week overrides +1 count:** a scenario where the lowest-count matching forces a within-last-week rematch, and an alternative matching avoids it at the cost of exactly +1 total count — assert the alternative (no fresh rematch) is chosen. The companion guard: when avoiding the rematch costs +2 count, assert the lower-count matching wins instead. Together these pin the 1000 < 1500 < 2000 boundary.
5. **Bipartite equal sizes:** 3 learners × 3 teachers with known counts → assert specific assignment minimizing total.
6. **Bipartite unequal sizes:** 4 learners × 2 teachers → exactly 2 pairs returned; the 2 leftover learners appear in `unmatched`, and they are the ones whose exclusion frees a cheaper assignment.
7. **Bipartite empty sides:**
   - Both empty → empty pairings, empty unmatched
   - One side empty → empty pairings, full other side in unmatched.

No integration test in this iteration. The existing `LeastPlayedPairingStrategyIT` (with the dump-based DB setup) covers the integration pattern; a parallel `BlossomPairingStrategyIT` can be added in a follow-up if real-data validation is wanted.

## Out of scope / deferred

- Shared `PairingHistory` helper unifying repository access between strategies.
- Bye-count fairness (spreading byes across players over a season).
- Recency-aware bye weighting.
- Integration test under `BlossomPairingStrategy`.
- Configuration of `COUNT_WEIGHT` / the recency bracket boundaries and penalties via `application.properties` — all stay as hard-coded constants until there's a real reason to tune them.
