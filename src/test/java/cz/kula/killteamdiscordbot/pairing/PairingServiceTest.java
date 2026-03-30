package cz.kula.killteamdiscordbot.pairing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
        var voters = List.of("user-1", "user-2", "user-3");
        var pairingResults = List.of(
                new PairingResult("user-1", "user-2"),
                new PairingResult("user-3", null)
        );
        when(pairingStrategy.generatePairings(voters)).thenReturn(pairingResults);
        when(pairingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var result = pairingService.createPairings(1L, voters);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getPollId()).isEqualTo(1L);
        assertThat(result.get(0).getPlayer1DiscordUserId()).isEqualTo("user-1");
        assertThat(result.get(0).getPlayer2DiscordUserId()).isEqualTo("user-2");
        assertThat(result.get(1).getPlayer2DiscordUserId()).isNull();
    }

    @Test
    void createPairingsWithFewerThanTwoVotersReturnsEmpty() {
        var result = pairingService.createPairings(1L, List.of("user-1"));

        assertThat(result).isEmpty();
        verify(pairingStrategy, never()).generatePairings(any());
        verify(pairingRepository, never()).saveAll(any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void createPairingsSetsPollIdAndCreatedAt() {
        var voters = List.of("user-1", "user-2");
        when(pairingStrategy.generatePairings(voters))
                .thenReturn(List.of(new PairingResult("user-1", "user-2")));
        when(pairingRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        pairingService.createPairings(42L, voters);

        ArgumentCaptor<List<Pairing>> captor = ArgumentCaptor.forClass(List.class);
        verify(pairingRepository).saveAll(captor.capture());
        var saved = captor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.getFirst().getPollId()).isEqualTo(42L);
        assertThat(saved.getFirst().getCreatedAt()).isNotNull();
    }
}
