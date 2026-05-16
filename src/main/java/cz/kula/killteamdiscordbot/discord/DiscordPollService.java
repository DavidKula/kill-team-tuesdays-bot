package cz.kula.killteamdiscordbot.discord;

import cz.kula.killteamdiscordbot.poll.Poll;
import cz.kula.killteamdiscordbot.poll.PollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.utils.messages.MessagePollBuilder;
import net.dv8tion.jda.api.utils.messages.MessagePollData;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class DiscordPollService {

    private final JDA jda;
    private final PollService pollService;

    public void sendPoll(Poll poll, Duration duration) {
        log.info("DiscordPollService#sendPoll({}, {})", poll, duration);
        TextChannel channel = jda.getTextChannelById(poll.getDiscordChannelId());
        if (channel == null) {
            log.error("Channel not found: {}", poll.getDiscordChannelId());
            return;
        }

        MessagePollBuilder pollBuilder = MessagePollData.builder(poll.getQuestion())
                .setDuration(duration);

        poll.getOptions().forEach(option -> pollBuilder.addAnswer(option.getOptionText()));

        channel.sendMessagePoll(pollBuilder.build())
                .queue(
                        message -> {
                            pollService.updateDiscordMessageId(poll.getId(), message.getId());
                            log.info("Poll sent to Discord. messageId={}", message.getId());
                        },
                        error -> {
                            log.error("Failed to send poll to Discord", error);
                            throw new RuntimeException("Failed to send poll to Discord", error);
                        }
                );
    }
}
