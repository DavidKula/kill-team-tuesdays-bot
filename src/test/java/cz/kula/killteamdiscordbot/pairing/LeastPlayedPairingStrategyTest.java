package cz.kula.killteamdiscordbot.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeastPlayedPairingStrategyTest {

    @Mock
    private PairingRepository pairingRepository;

    @InjectMocks
    private LeastPlayedPairingStrategy strategy;

    @Test
    void emptyListProducesNoPairings() {
        var result = strategy.generatePairings(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void onePlayerProducesOneBye() {
        var result = strategy.generatePairings(List.of("a"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().player1DiscordUserId()).isEqualTo("a");
        assertThat(result.getFirst().player2DiscordUserId()).isNull();
    }

    @Test
    void twoPlayersWithNoHistoryArePaired() {
        when(pairingRepository.findByPlayerIds(anyList())).thenReturn(List.of());

        var result = strategy.generatePairings(List.of("a", "b"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().player1DiscordUserId()).isEqualTo("a");
        assertThat(result.getFirst().player2DiscordUserId()).isEqualTo("b");
    }

    @Test
    void pairsPlayersWhoHavePlayedEachOtherLeast() {
        // a-b played 3 times, a-c played 1 time, b-c played 2 times
        // Expected: a-c (1 game) paired first, b gets a bye
        var pastPairings = List.of(
                pairing("a", "b"),
                pairing("a", "b"),
                pairing("a", "b"),
                pairing("a", "c"),
                pairing("b", "c"),
                pairing("b", "c")
        );
        when(pairingRepository.findByPlayerIds(anyList())).thenReturn(pastPairings);

        var result = strategy.generatePairings(List.of("a", "b", "c"));

        assertThat(result).hasSize(2);

        var fullPairing = result.stream()
                .filter(p -> p.player2DiscordUserId() != null)
                .findFirst().orElseThrow();
        assertThat(fullPairing.player1DiscordUserId()).isEqualTo("a");
        assertThat(fullPairing.player2DiscordUserId()).isEqualTo("c");

        var bye = result.stream()
                .filter(p -> p.player2DiscordUserId() == null)
                .findFirst().orElseThrow();
        assertThat(bye.player1DiscordUserId()).isEqualTo("b");
    }

    @Test
    void fourPlayersWithHistoryPairsLeastPlayed() {
        // a-b: 3, a-c: 0, a-d: 1, b-c: 2, b-d: 0, c-d: 1
        // Sorted: a-c(0), b-d(0), a-d(1), c-d(1), b-c(2), a-b(3)
        // Greedy: pick a-c(0), then b-d(0)
        var pastPairings = List.of(
                pairing("a", "b"),
                pairing("a", "b"),
                pairing("a", "b"),
                pairing("a", "d"),
                pairing("b", "c"),
                pairing("b", "c"),
                pairing("c", "d")
        );
        when(pairingRepository.findByPlayerIds(anyList())).thenReturn(pastPairings);

        var result = strategy.generatePairings(List.of("a", "b", "c", "d"));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(p -> assertThat(p.player2DiscordUserId()).isNotNull());

        // a-c should be paired (0 games)
        assertThat(result).anySatisfy(p -> {
            assertThat(p.player1DiscordUserId()).isEqualTo("a");
            assertThat(p.player2DiscordUserId()).isEqualTo("c");
        });
        // b-d should be paired (0 games)
        assertThat(result).anySatisfy(p -> {
            assertThat(p.player1DiscordUserId()).isEqualTo("b");
            assertThat(p.player2DiscordUserId()).isEqualTo("d");
        });
    }

    @Test
    void evenNumberWithNoHistoryPairsSequentially() {
        when(pairingRepository.findByPlayerIds(anyList())).thenReturn(List.of());

        var result = strategy.generatePairings(List.of("a", "b", "c", "d"));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(p -> {
            assertThat(p.player1DiscordUserId()).isNotNull();
            assertThat(p.player2DiscordUserId()).isNotNull();
        });
    }

    private Pairing pairing(String player1, String player2) {
        return Pairing.builder()
                .player1DiscordUserId(player1)
                .player2DiscordUserId(player2)
                .createdAt(OffsetDateTime.now())
                .build();
    }
}
