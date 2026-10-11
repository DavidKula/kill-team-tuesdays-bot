package cz.kula.killteamdiscordbot.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairingServiceTest {

    @Mock
    private PairingRepository pairingRepository;

    @Mock
    private PairingStrategy pairingStrategy;

    @InjectMocks
    private PairingService pairingService;

    @Test
    void createPairingsCallsStrategyAndSaves() {
        var yesVoters = List.of("user-1", "user-2", "user-3");
        var pairingResults = List.of(
                new PairingResult("user-1", "user-2"),
                new PairingResult("user-3", null)
        );
        when(pairingStrategy.generateBipartitePairings(List.of(), List.of()))
                .thenReturn(new BipartiteMatchResult(List.of(), List.of()));
        when(pairingStrategy.generatePairings(yesVoters)).thenReturn(pairingResults);
        when(pairingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = pairingService.createPairings(1L, yesVoters, List.of(), List.of());

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPollId()).isEqualTo(1L);
        assertThat(result.get(0).getPlayer1DiscordUserId()).isEqualTo("user-1");
        assertThat(result.get(0).getPlayer2DiscordUserId()).isEqualTo("user-2");
        assertThat(result.get(1).getPlayer2DiscordUserId()).isNull();
    }

    @Test
    void createPairingsWithFewerThanTwoVotersReturnsEmpty() {
        var result = pairingService.createPairings(1L, List.of("user-1"), List.of(), List.of());

        assertThat(result).isEmpty();
        verify(pairingStrategy, never()).generatePairings(any());
        verify(pairingStrategy, never()).generateBipartitePairings(any(), any());
        verify(pairingRepository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void createPairingsSetsPollIdAndCreatedAt() {
        var voters = List.of("user-1", "user-2");
        when(pairingStrategy.generateBipartitePairings(List.of(), List.of()))
                .thenReturn(new BipartiteMatchResult(List.of(), List.of()));
        when(pairingStrategy.generatePairings(voters))
                .thenReturn(List.of(new PairingResult("user-1", "user-2")));
        when(pairingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        pairingService.createPairings(42L, voters, List.of(), List.of());

        ArgumentCaptor<List<Pairing>> captor = ArgumentCaptor.forClass(List.class);
        verify(pairingRepository).saveAll(captor.capture());
        var saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getPollId()).isEqualTo(42L);
        assertThat(saved.getFirst().getCreatedAt()).isNotNull();
    }

    @Test
    void createPairingsPairsLearningWithTeachingFirst() {
        var learning = List.of("L1", "L2");
        var teaching = List.of("T1", "T2");
        var bipartitePairings = List.of(
                new PairingResult("L1", "T1"),
                new PairingResult("L2", "T2")
        );
        when(pairingStrategy.generateBipartitePairings(learning, teaching))
                .thenReturn(new BipartiteMatchResult(bipartitePairings, List.of()));
        when(pairingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = pairingService.createPairings(1L, List.of(), learning, teaching);

        assertThat(result).hasSize(2);
        assertThat(result).anySatisfy(p -> {
            assertThat(p.getPlayer1DiscordUserId()).isEqualTo("L1");
            assertThat(p.getPlayer2DiscordUserId()).isEqualTo("T1");
        });
        assertThat(result).anySatisfy(p -> {
            assertThat(p.getPlayer1DiscordUserId()).isEqualTo("L2");
            assertThat(p.getPlayer2DiscordUserId()).isEqualTo("T2");
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void createPairingsMergesBipartiteUnmatchedIntoYesPool() {
        // 2 learners + 1 teacher → 1 bipartite pair, 1 learner left over → goes into yes pool with the yes voter
        var yesVoters = List.of("Y1");
        var learning = List.of("L1", "L2");
        var teaching = List.of("T1");
        when(pairingStrategy.generateBipartitePairings(learning, teaching))
                .thenReturn(new BipartiteMatchResult(
                        List.of(new PairingResult("L1", "T1")),
                        List.of("L2")));
        when(pairingStrategy.generatePairings(anyList()))
                .thenReturn(List.of(new PairingResult("Y1", "L2")));
        when(pairingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = pairingService.createPairings(1L, yesVoters, learning, teaching);

        assertThat(result).hasSize(2);
        ArgumentCaptor<List<String>> poolCaptor = ArgumentCaptor.forClass(List.class);
        verify(pairingStrategy).generatePairings(poolCaptor.capture());
        assertThat(poolCaptor.getValue()).containsExactlyInAnyOrder("Y1", "L2");
    }

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
        assertThat(result.getFirst().getPlayer1DiscordUserId()).isEqualTo("L1");
        assertThat(result.getFirst().getPlayer2DiscordUserId()).isEqualTo("T1");
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
}
