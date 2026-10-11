# Tutored Trio Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When the weekly player count is odd and there are ≥2 learners and ≥1 tutor, replace the bye with a tutored trio (1 random tutor observing 2 random learners).

**Architecture:** `PairingService.createPairings` removes a randomly chosen trio before the existing bipartite + Blossom pipeline runs. The trio is persisted as one `pairings` row (`player1`/`player2` = learners, new nullable `tutor_discord_user_id` = tutor). `PairingResult` gains a nullable `tutorDiscordUserId`; the listener carries it to the Discord message, which renders `N. <@L1> vs <@L2> (tutored by <@T>)`.

**Tech Stack:** Java 21, Spring Boot 4, Spring Data JPA, Flyway/PostgreSQL, JUnit 5 + Mockito + AssertJ, Testcontainers.

**Spec:** `docs/superpowers/specs/2026-10-01-tutored-trio-design.md`

## Global Constraints

- Trigger: player count (Yes + Learning + Teaching voters) is odd **and** learners ≥ 2 **and** tutors ≥ 1. Otherwise behaviour is unchanged.
- Trio selection is random (`Collections.shuffle`), no history involved. At most one trio per week.
- Pairing history is unchanged: only `player1`/`player2` count; the tutor column is never read by strategies.
- New column: `tutor_discord_user_id VARCHAR(64)`, nullable, migration `V3__add_tutor_to_pairings.sql`.
- Message line for a trio: `N. <@L1> vs <@L2> (tutored by <@T>)`.
- **Do not run git commands.** The user handles all git operations; "Commit" steps mean: stop and tell the user the task is ready to commit.
- Use system Maven offline: `mvn -o ...` (the wrapper does not work). Full `verify` needs Docker running (Testcontainers).

---

### Task 1: Data model + trio formation in `PairingService`

**Files:**
- Create: `src/main/resources/db/migration/V3__add_tutor_to_pairings.sql`
- Modify: `src/main/java/cz/kula/killteamdiscordbot/pairing/PairingResult.java`
- Modify: `src/main/java/cz/kula/killteamdiscordbot/pairing/Pairing.java`
- Modify: `src/main/java/cz/kula/killteamdiscordbot/pairing/PairingService.java`
- Test: `src/test/java/cz/kula/killteamdiscordbot/pairing/PairingServiceTest.java`

**Interfaces:**
- Produces:
  - `record PairingResult(String player1DiscordUserId, @Nullable String player2DiscordUserId, @Nullable String tutorDiscordUserId)` plus 2-arg constructor `PairingResult(String, @Nullable String)` → tutor `null`.
  - `Pairing#getTutorDiscordUserId(): @Nullable String`, builder method `tutorDiscordUserId(String)`.
  - `PairingService.createPairings(Long, List<String>, List<String>, List<String>)` — signature unchanged; may now return one `Pairing` with non-null tutor.

- [ ] **Step 1: Write the failing tests**

Append to `PairingServiceTest` (new imports: `java.util.ArrayList`, `java.util.stream.Stream`). The trio tests use the real, dependency-free `RandomPairingStrategy` so the remaining players are actually paired.

```java
    @Test
    void oddCountWithTwoLearnersAndTutorFormsTrio() {
        assertTrioReplacesBye(List.of(), List.of("L1", "L2"), List.of("T1"));
    }

    @Test
    void trioLeavesExtraLearnerToBePairedWithYesVoter() {
        assertTrioReplacesBye(List.of("Y1"), List.of("L1", "L2", "L3"), List.of("T1"));
    }

    @Test
    void trioLeavesYesVotersToPlayEachOther() {
        assertTrioReplacesBye(List.of("Y1", "Y2"), List.of("L1", "L2"), List.of("T1"));
    }

    @Test
    void trioLeavesRemainingLearnerAndTutorPaired() {
        assertTrioReplacesBye(List.of(), List.of("L1", "L2", "L3"), List.of("T1", "T2"));
    }

    @Test
    void evenCountFormsNoTrio() {
        var result = serviceWithRandomStrategy().createPairings(1L, List.of("Y1"), List.of("L1", "L2"), List.of("T1"));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(p -> assertThat(p.getTutorDiscordUserId()).isNull());
    }

    @Test
    void oddCountWithSingleLearnerKeepsBye() {
        var result = serviceWithRandomStrategy().createPairings(1L, List.of("Y1"), List.of("L1"), List.of("T1"));

        assertThat(result).allSatisfy(p -> assertThat(p.getTutorDiscordUserId()).isNull());
        assertThat(result).filteredOn(p -> p.getPlayer2DiscordUserId() == null).hasSize(1);
    }

    @Test
    void oddCountWithoutTutorKeepsBye() {
        var result = serviceWithRandomStrategy().createPairings(1L, List.of("Y1"), List.of("L1", "L2"), List.of());

        assertThat(result).allSatisfy(p -> assertThat(p.getTutorDiscordUserId()).isNull());
        assertThat(result).filteredOn(p -> p.getPlayer2DiscordUserId() == null).hasSize(1);
    }

    private PairingService serviceWithRandomStrategy() {
        when(pairingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return new PairingService(pairingRepository, new RandomPairingStrategy());
    }

    private void assertTrioReplacesBye(List<String> yes, List<String> learning, List<String> teaching) {
        var result = serviceWithRandomStrategy().createPairings(1L, yes, learning, teaching);

        var trios = result.stream().filter(p -> p.getTutorDiscordUserId() != null).toList();
        assertThat(trios).hasSize(1);
        var trio = trios.getFirst();
        assertThat(teaching).contains(trio.getTutorDiscordUserId());
        assertThat(learning).contains(trio.getPlayer1DiscordUserId(), trio.getPlayer2DiscordUserId());
        assertThat(trio.getPlayer1DiscordUserId()).isNotEqualTo(trio.getPlayer2DiscordUserId());

        assertThat(result).allSatisfy(p -> assertThat(p.getPlayer2DiscordUserId()).isNotNull());

        var everyone = new ArrayList<String>();
        result.forEach(p -> {
            everyone.add(p.getPlayer1DiscordUserId());
            everyone.add(p.getPlayer2DiscordUserId());
            if (p.getTutorDiscordUserId() != null) {
                everyone.add(p.getTutorDiscordUserId());
            }
        });
        assertThat(everyone).containsExactlyInAnyOrderElementsOf(
                Stream.of(yes, learning, teaching).flatMap(List::stream).toList());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -o test -Dtest=PairingServiceTest`
Expected: COMPILATION ERROR — `cannot find symbol: method getTutorDiscordUserId()`.

- [ ] **Step 3: Add the migration**

`src/main/resources/db/migration/V3__add_tutor_to_pairings.sql`:

```sql
ALTER TABLE pairings ADD COLUMN tutor_discord_user_id VARCHAR(64);
```

- [ ] **Step 4: Extend `PairingResult`**

Replace the record in `PairingResult.java`:

```java
public record PairingResult(String player1DiscordUserId,
                            @Nullable String player2DiscordUserId,
                            @Nullable String tutorDiscordUserId) {

    public PairingResult(String player1DiscordUserId, @Nullable String player2DiscordUserId) {
        this(player1DiscordUserId, player2DiscordUserId, null);
    }
}
```

- [ ] **Step 5: Extend `Pairing` entity**

In `Pairing.java`, after `player2DiscordUserId`:

```java
    @Column(name = "tutor_discord_user_id")
    private @Nullable String tutorDiscordUserId;
```

- [ ] **Step 6: Form the trio in `PairingService`**

Replace the body of `createPairings` from the bipartite call onward (keep the log line and the `< 2` guard), and add the helper. New import: `java.util.Collections`.

```java
        var learners = new ArrayList<>(learningVoterDiscordUserIds);
        var tutors = new ArrayList<>(teachingVoterDiscordUserIds);
        var allResults = new ArrayList<PairingResult>();
        if (totalVoters % 2 != 0 && learners.size() >= 2 && !tutors.isEmpty()) {
            allResults.add(pickTutoredTrio(learners, tutors));
        }

        BipartiteMatchResult bipartite = pairingStrategy.generateBipartitePairings(learners, tutors);

        var remainingPool = new ArrayList<String>(yesVoterDiscordUserIds.size() + bipartite.unmatched().size());
        remainingPool.addAll(yesVoterDiscordUserIds);
        remainingPool.addAll(bipartite.unmatched());

        List<PairingResult> remainingPairings = remainingPool.size() >= 2
                ? pairingStrategy.generatePairings(remainingPool)
                : remainingPool.stream().map(id -> new PairingResult(id, null)).toList();

        allResults.addAll(bipartite.pairings());
        allResults.addAll(remainingPairings);

        var now = OffsetDateTime.now();
        var pairings = allResults.stream()
                .map(result -> Pairing.builder()
                        .pollId(pollId)
                        .player1DiscordUserId(result.player1DiscordUserId())
                        .player2DiscordUserId(result.player2DiscordUserId())
                        .tutorDiscordUserId(result.tutorDiscordUserId())
                        .createdAt(now)
                        .build())
                .toList();
        return pairingRepository.saveAll(pairings);
    }

    private PairingResult pickTutoredTrio(List<String> learners, List<String> tutors) {
        Collections.shuffle(learners);
        Collections.shuffle(tutors);
        var trio = new PairingResult(learners.removeFirst(), learners.removeFirst(), tutors.removeFirst());
        log.info("Tutored trio: {}", trio);
        return trio;
    }
```

Note: lists are copied before shuffling, so callers' lists are never mutated, and without a trio the copies equal the inputs (existing mock stubs like `generateBipartitePairings(learning, teaching)` still match).

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn -o test -Dtest=PairingServiceTest`
Expected: PASS (5 existing + 7 new).

- [ ] **Step 8: Hand off for commit**

Tell the user Task 1 is ready (suggested message: `feat: form tutored trio instead of bye`). Do not run git.

---

### Task 2: Carry the tutor to the Discord message

**Files:**
- Modify: `src/main/java/cz/kula/killteamdiscordbot/weeklyattendancepoll/PollClosedEventListener.java:32-34`
- Modify: `src/main/java/cz/kula/killteamdiscordbot/weeklyattendancepoll/PairingsMessageService.java:58-74`
- Test: `src/test/java/cz/kula/killteamdiscordbot/weeklyattendancepoll/PollClosedEventListenerTest.java`
- Test: `src/test/java/cz/kula/killteamdiscordbot/weeklyattendancepoll/PairingsMessageServiceTest.java`

**Interfaces:**
- Consumes: `PairingResult(String, String, String)` 3-arg constructor and `tutorDiscordUserId()`; `Pairing#getTutorDiscordUserId()` and `Pairing.builder().tutorDiscordUserId(..)` (Task 1).

- [ ] **Step 1: Write the failing tests**

Append to `PollClosedEventListenerTest` (new imports: `cz.kula.killteamdiscordbot.pairing.Pairing`, `cz.kula.killteamdiscordbot.pairing.PairingResult`):

```java
    @Test
    void tutorIsPassedToPublishedPairing() {
        when(pollService.getVoterDiscordUserIdsByOptionIndex(POLL_ID, 5)).thenReturn(List.of());
        when(pairingService.createPairings(POLL_ID, List.of("yes-1"), List.of(), List.of())).thenReturn(List.of(
                Pairing.builder().player1DiscordUserId("L1").player2DiscordUserId("L2").tutorDiscordUserId("T1").build()));

        listener.on(new PollClosedEvent(POLL_ID));

        verify(eventPublisher).publishEvent(new PollProcessingFinishedEvent(POLL_ID, CHANNEL_ID,
                List.of(new PairingResult("L1", "L2", "T1")), List.of()));
    }
```

Append to `PairingsMessageServiceTest`:

```java
    @Test
    void trioIsListedAsMatchWithTutor() {
        pairingsMessageService.on(new PollProcessingFinishedEvent(POLL_ID, CHANNEL_ID, List.of(
                new PairingResult("a", "b"),
                new PairingResult("c", "d", "t")), List.of()));

        assertThat(sentMessage()).isEqualTo("""
                **This week's Kill Team pairings:**

                1. <@a> vs <@b>
                2. <@c> vs <@d> (tutored by <@t>)""");
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -o test -Dtest="PollClosedEventListenerTest,PairingsMessageServiceTest"`
Expected: FAIL — listener publishes `PairingResult[..., tutorDiscordUserId=null]`; message lacks `(tutored by <@t>)`.

- [ ] **Step 3: Map tutor in `PollClosedEventListener`**

```java
            var pairingResults = pairings.stream()
                    .map(p -> new PairingResult(p.getPlayer1DiscordUserId(), p.getPlayer2DiscordUserId(), p.getTutorDiscordUserId()))
                    .toList();
```

- [ ] **Step 4: Render tutor in `PairingsMessageService.formatPairingsMessage`**

Replace the `if` branch for a paired match:

```java
            if (pairing.player2DiscordUserId() != null) {
                sb.append(matchNumber++).append(". ")
                        .append("<@").append(pairing.player1DiscordUserId()).append(">")
                        .append(" vs ")
                        .append("<@").append(pairing.player2DiscordUserId()).append(">");
                if (pairing.tutorDiscordUserId() != null) {
                    sb.append(" (tutored by <@").append(pairing.tutorDiscordUserId()).append(">)");
                }
                sb.append("\n");
            } else {
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn -o test -Dtest="PollClosedEventListenerTest,PairingsMessageServiceTest"`
Expected: PASS.

- [ ] **Step 6: Full verification (needs Docker)**

Run: `mvn -o clean verify`
Expected: BUILD SUCCESS; Testcontainers ITs pass, proving V3 migration matches the entity under `ddl-auto=validate`. Test count rises from 45 to 54.

- [ ] **Step 7: Hand off for commit**

Tell the user Task 2 is ready (suggested message: `feat: show tutor in pairings message`). Do not run git.
