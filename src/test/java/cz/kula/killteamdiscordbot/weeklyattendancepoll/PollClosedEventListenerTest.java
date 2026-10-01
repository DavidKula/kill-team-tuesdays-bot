package cz.kula.killteamdiscordbot.weeklyattendancepoll;

import cz.kula.killteamdiscordbot.pairing.PairingService;
import cz.kula.killteamdiscordbot.poll.Poll;
import cz.kula.killteamdiscordbot.poll.PollClosedEvent;
import cz.kula.killteamdiscordbot.poll.PollService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollClosedEventListenerTest {

    private static final long POLL_ID = 1L;
    private static final String CHANNEL_ID = "channel-1";

    @Mock
    private PollService pollService;

    @Mock
    private PairingService pairingService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PollClosedEventListener listener;

    @BeforeEach
    void setUp() {
        when(pollService.getPoll(POLL_ID)).thenReturn(Poll.builder().id(POLL_ID).discordChannelId(CHANNEL_ID).build());
        when(pollService.getVoterDiscordUserIdsByOptionIndex(POLL_ID, 1)).thenReturn(List.of("yes-1"));
        when(pollService.getVoterDiscordUserIdsByOptionIndex(POLL_ID, 3)).thenReturn(List.of());
        when(pollService.getVoterDiscordUserIdsByOptionIndex(POLL_ID, 4)).thenReturn(List.of());
    }

    @Test
    void arrangedVotersAreNotPassedToPairing() {
        when(pollService.getVoterDiscordUserIdsByOptionIndex(POLL_ID, 5)).thenReturn(List.of("arranged-1"));
        when(pairingService.createPairings(POLL_ID, List.of("yes-1"), List.of(), List.of())).thenReturn(List.of());

        listener.on(new PollClosedEvent(POLL_ID));

        verify(pairingService).createPairings(POLL_ID, List.of("yes-1"), List.of(), List.of());
    }

    @Test
    void eventIsPublishedWhenOnlyArrangedVotersExist() {
        when(pollService.getVoterDiscordUserIdsByOptionIndex(POLL_ID, 5)).thenReturn(List.of("arranged-1"));
        when(pairingService.createPairings(POLL_ID, List.of("yes-1"), List.of(), List.of())).thenReturn(List.of());

        listener.on(new PollClosedEvent(POLL_ID));

        verify(eventPublisher).publishEvent(new PollProcessingFinishedEvent(POLL_ID, CHANNEL_ID, List.of(), List.of("arranged-1")));
    }

    @Test
    void noEventIsPublishedWithoutPairingsOrArrangedVoters() {
        when(pollService.getVoterDiscordUserIdsByOptionIndex(POLL_ID, 5)).thenReturn(List.of());
        when(pairingService.createPairings(POLL_ID, List.of("yes-1"), List.of(), List.of())).thenReturn(List.of());

        listener.on(new PollClosedEvent(POLL_ID));

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }
}
