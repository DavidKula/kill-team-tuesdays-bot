package cz.kula.killteamdiscordbot.discord;

import cz.kula.killteamdiscordbot.poll.PollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.events.message.MessageUpdateEvent;
import net.dv8tion.jda.api.events.message.poll.MessagePollVoteAddEvent;
import net.dv8tion.jda.api.events.message.poll.MessagePollVoteRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PollVoteEventListener extends ListenerAdapter {

    private final PollService pollService;

    @Override
    public void onMessagePollVoteAdd(MessagePollVoteAddEvent event) {
        log.info("PollVoteEventListener#onMessagePollVoteAdd({})", event);
        String messageId = event.getMessageId();
        long answerId = event.getAnswerId();
        String userId = event.getUserId();
        pollService.recordVote(messageId, answerId, userId);
    }

    @Override
    public void onMessageUpdate(MessageUpdateEvent event) {
        log.info("PollVoteEventListener#onMessageUpdate({})", event);
        var poll = event.getMessage().getPoll();
        if (poll != null && poll.isExpired()) {
            pollService.closePoll(event.getMessageId());
        }
    }

    @Override
    public void onMessagePollVoteRemove(MessagePollVoteRemoveEvent event) {
        log.info("PollVoteEventListener#onMessagePollVoteRemove({})", event);
        String messageId = event.getMessageId();
        long answerId = event.getAnswerId();
        String userId = event.getUserId();
        pollService.removeVote(messageId, answerId, userId);
    }
}
