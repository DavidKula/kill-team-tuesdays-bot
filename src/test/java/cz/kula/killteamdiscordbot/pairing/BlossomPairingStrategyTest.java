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
import static org.mockito.Mockito.verifyNoInteractions;
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
        verifyNoInteractions(pairingRepository);
    }

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

    @Test
    void duplicateInputIdsAreDeduplicatedWithoutError() {
        stubPast(List.of());

        var result = strategy.generatePairings(List.of("a", "a", "b"));

        // After de-duplication the players are {a, b} -> a single pair, no error.
        assertThat(result).hasSize(1);
        assertThat(pairSet(result)).containsExactly(Set.of("a", "b"));
    }

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
        // Diagonal is free (0 plays); every off-diagonal pair has 5 plays (weight 5000). Optimum is the diagonal.
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

    @Test
    void bipartiteDuplicateIdsWithinAGroupAreDeduplicated() {
        stubPast(List.of());

        var result = strategy.generateBipartitePairings(List.of("la", "la", "lb"), List.of("ta"));

        // Learners de-duplicate to {la, lb}; one teacher -> exactly one pairing, one learner unmatched.
        assertThat(result.pairings()).hasSize(1);
        assertThat(result.pairings().getFirst().player2DiscordUserId()).isEqualTo("ta");
        assertThat(result.unmatched()).hasSize(1);
    }

    @Test
    void bipartiteMoreTeachersThanLearnersLeavesSurplusTeachersUnmatched() {
        // 2 learners, 4 teachers. la-ta and lb-tb are free; everything else has 5 plays (weight 5000).
        // Optimum pairs la-ta, lb-tb; tc and td get padded to dummies and become unmatched.
        stubPast(List.of(
                pairing("la", "tb", 100), pairing("la", "tb", 100), pairing("la", "tb", 100),
                pairing("la", "tb", 100), pairing("la", "tb", 100),
                pairing("la", "tc", 100), pairing("la", "tc", 100), pairing("la", "tc", 100),
                pairing("la", "tc", 100), pairing("la", "tc", 100),
                pairing("la", "td", 100), pairing("la", "td", 100), pairing("la", "td", 100),
                pairing("la", "td", 100), pairing("la", "td", 100),
                pairing("lb", "ta", 100), pairing("lb", "ta", 100), pairing("lb", "ta", 100),
                pairing("lb", "ta", 100), pairing("lb", "ta", 100),
                pairing("lb", "tc", 100), pairing("lb", "tc", 100), pairing("lb", "tc", 100),
                pairing("lb", "tc", 100), pairing("lb", "tc", 100),
                pairing("lb", "td", 100), pairing("lb", "td", 100), pairing("lb", "td", 100),
                pairing("lb", "td", 100), pairing("lb", "td", 100)
        ));

        var result = strategy.generateBipartitePairings(
                List.of("la", "lb"), List.of("ta", "tb", "tc", "td"));

        assertThat(result.pairings()).containsExactlyInAnyOrder(
                new PairingResult("la", "ta"),
                new PairingResult("lb", "tb"));
        assertThat(result.unmatched()).containsExactlyInAnyOrder("tc", "td");
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
