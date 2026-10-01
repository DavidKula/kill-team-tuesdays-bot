package cz.kula.killteamdiscordbot.weeklyattendancepoll;

import cz.kula.killteamdiscordbot.pairing.PairingResult;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PairingsMessageServiceTest {

    private static final long POLL_ID = 1L;
    private static final String CHANNEL_ID = "channel-1";

    @Mock
    private JDA jda;

    @Mock
    private TextChannel channel;

    @Mock
    private MessageCreateAction messageCreateAction;

    @InjectMocks
    private PairingsMessageService pairingsMessageService;

    @BeforeEach
    void setUp() {
        when(jda.getTextChannelById(CHANNEL_ID)).thenReturn(channel);
        when(channel.sendMessage(any(CharSequence.class))).thenReturn(messageCreateAction);
    }

    @Test
    void messageWithoutArrangedPlayersListsOnlyPairings() {
        pairingsMessageService.on(new PollProcessingFinishedEvent(POLL_ID, CHANNEL_ID, List.of(
                new PairingResult("a", "b"),
                new PairingResult("c", null)), List.of()));

        assertThat(sentMessage()).isEqualTo("""
                **This week's Kill Team pairings:**

                1. <@a> vs <@b>

                <@c> has no explicit pairing. Feel free to look for your own :)""");
    }

    @Test
    void messageListsArrangedPlayersAfterPairings() {
        pairingsMessageService.on(new PollProcessingFinishedEvent(POLL_ID, CHANNEL_ID, List.of(
                new PairingResult("a", "b")), List.of("e", "f")));

        assertThat(sentMessage()).isEqualTo("""
                **This week's Kill Team pairings:**

                1. <@a> vs <@b>

                **Already arranged their own games:** <@e>, <@f>""");
    }

    @Test
    void messageWithOnlyArrangedPlayersOmitsPairingsHeader() {
        pairingsMessageService.on(new PollProcessingFinishedEvent(POLL_ID, CHANNEL_ID, List.of(), List.of("e")));

        assertThat(sentMessage()).isEqualTo("**Already arranged their own games:** <@e>");
    }

    private String sentMessage() {
        var captor = ArgumentCaptor.forClass(CharSequence.class);
        verify(channel).sendMessage(captor.capture());
        return captor.getValue().toString();
    }
}
