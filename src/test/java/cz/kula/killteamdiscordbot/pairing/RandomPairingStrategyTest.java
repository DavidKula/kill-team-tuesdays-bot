package cz.kula.killteamdiscordbot.pairing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RandomPairingStrategyTest {

    private final RandomPairingStrategy strategy = new RandomPairingStrategy();

    @Test
    void evenNumberOfPlayersProducesAllFullPairings() {
        var result = strategy.generatePairings(List.of("a", "b", "c", "d"));

        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(p -> {
            assertThat(p.player1DiscordUserId()).isNotNull();
            assertThat(p.player2DiscordUserId()).isNotNull();
        });
    }

    @Test
    void oddNumberOfPlayersProducesOneBye() {
        var result = strategy.generatePairings(List.of("a", "b", "c"));

        assertThat(result).hasSize(2);
        long byeCount = result.stream()
                .filter(p -> p.player2DiscordUserId() == null)
                .count();
        assertThat(byeCount).isEqualTo(1);
    }

    @Test
    void twoPlayersProducesOnePairing() {
        var result = strategy.generatePairings(List.of("a", "b"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().player1DiscordUserId()).isNotNull();
        assertThat(result.getFirst().player2DiscordUserId()).isNotNull();
    }

    @Test
    void onePlayerProducesOneBye() {
        var result = strategy.generatePairings(List.of("a"));

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().player1DiscordUserId()).isEqualTo("a");
        assertThat(result.getFirst().player2DiscordUserId()).isNull();
    }

    @Test
    void emptyListProducesNoPairings() {
        var result = strategy.generatePairings(List.of());

        assertThat(result).isEmpty();
    }
}
