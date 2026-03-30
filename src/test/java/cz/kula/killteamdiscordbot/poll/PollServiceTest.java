package cz.kula.killteamdiscordbot.poll;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PollServiceTest {

    @Mock
    private PollRepository pollRepository;

    @Mock
    private PollOptionRepository pollOptionRepository;

    @Mock
    private PollVoteRepository pollVoteRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PollService pollService;

    @Test
    void createPollSavesWithOptionsCorrectly() {
        List<String> options = List.of("Monday", "Tuesday", "Wednesday");
        Duration duration = Duration.ofDays(7);

        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Poll result = pollService.createPoll("channel-1", "Available?", options, duration);

        assertThat(result.getDiscordChannelId()).isEqualTo("channel-1");
        assertThat(result.getQuestion()).isEqualTo("Available?");
        assertThat(result.getOptions()).hasSize(3);
        assertThat(result.getOptions().get(0).getOptionText()).isEqualTo("Monday");
        assertThat(result.getOptions().get(0).getOptionIndex()).isZero();
        assertThat(result.getOptions().get(2).getOptionText()).isEqualTo("Wednesday");
        assertThat(result.getOptions().get(2).getOptionIndex()).isEqualTo(2);

        verify(pollRepository).save(any(Poll.class));
    }

    @Test
    void recordVoteSavesNewVote() {
        PollOption option = PollOption.builder().id(10L).optionText("Monday").build();
        when(pollOptionRepository.findByPollDiscordMessageIdAndOptionIndex("msg-1", 0))
                .thenReturn(Optional.of(option));
        when(pollVoteRepository.findByPollOptionIdAndDiscordUserId(10L, "user-1"))
                .thenReturn(Optional.empty());

        pollService.recordVote("msg-1", 0, "user-1");

        ArgumentCaptor<PollVote> captor = ArgumentCaptor.forClass(PollVote.class);
        verify(pollVoteRepository).save(captor.capture());
        assertThat(captor.getValue().getDiscordUserId()).isEqualTo("user-1");
        assertThat(captor.getValue().getPollOption()).isEqualTo(option);
    }

    @Test
    void recordVoteIgnoresDuplicateVote() {
        PollOption option = PollOption.builder().id(10L).optionText("Monday").build();
        PollVote existingVote = PollVote.builder().id(1L).build();

        when(pollOptionRepository.findByPollDiscordMessageIdAndOptionIndex("msg-1", 0))
                .thenReturn(Optional.of(option));
        when(pollVoteRepository.findByPollOptionIdAndDiscordUserId(10L, "user-1"))
                .thenReturn(Optional.of(existingVote));

        pollService.recordVote("msg-1", 0, "user-1");

        verify(pollVoteRepository, never()).save(any());
    }

    @Test
    void closePollPublishesPollClosedEvent() {
        Poll poll = Poll.builder().id(1L).discordMessageId("msg-1").state(PollState.RUNNING).build();
        when(pollRepository.findByDiscordMessageId("msg-1")).thenReturn(Optional.of(poll));
        when(pollRepository.save(any(Poll.class))).thenAnswer(invocation -> invocation.getArgument(0));

        pollService.closePoll("msg-1");

        assertThat(poll.getState()).isEqualTo(PollState.CLOSED);
        ArgumentCaptor<PollClosedEvent> captor = ArgumentCaptor.forClass(PollClosedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().pollId()).isEqualTo(1L);
    }

    @Test
    void closePollDoesNotPublishEventWhenPollNotFound() {
        when(pollRepository.findByDiscordMessageId("msg-unknown")).thenReturn(Optional.empty());

        pollService.closePoll("msg-unknown");

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void removeVoteDeletesExistingVote() {
        PollOption option = PollOption.builder().id(10L).optionText("Monday").build();
        when(pollOptionRepository.findByPollDiscordMessageIdAndOptionIndex("msg-1", 0))
                .thenReturn(Optional.of(option));

        pollService.removeVote("msg-1", 0, "user-1");

        verify(pollVoteRepository).deleteByPollOptionIdAndDiscordUserId(10L, "user-1");
    }
}
