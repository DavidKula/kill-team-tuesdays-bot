package cz.kula.killteamdiscordbot.weeklyattendancepoll;

import cz.kula.killteamdiscordbot.pairing.PairingResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class PairingsMessageService {

    private final JDA jda;

    @ApplicationModuleListener
    public void on(PollProcessingFinishedEvent event) {
        log.info("PairingsMessageService#on({})", event);
        TextChannel channel = jda.getTextChannelById(event.discordChannelId());
        if (channel == null) {
            log.error("Channel not found: {}", event.discordChannelId());
            throw new RuntimeException("Channel not found: " + event.discordChannelId());
        }

        String message = formatMessage(event.pairings(), event.arrangedVoterDiscordUserIds());
        try {
            channel.sendMessage(message).complete();
            log.info("Pairings message sent to channel {}", event.discordChannelId());
        } catch (Exception e) {
            log.error("Failed to send pairings message to channel {}", event.discordChannelId(), e);
            throw new RuntimeException("Failed to send pairings message to channel " + event.discordChannelId(), e);
        }
    }

    private String formatMessage(List<PairingResult> pairings, List<String> arrangedVoters) {
        var sections = new ArrayList<String>(2);
        if (!pairings.isEmpty()) {
            sections.add(formatPairingsMessage(pairings).strip());
        }
        if (!arrangedVoters.isEmpty()) {
            sections.add(formatArrangedMessage(arrangedVoters));
        }
        return String.join("\n\n", sections);
    }

    private String formatArrangedMessage(List<String> arrangedVoters) {
        return arrangedVoters.stream()
                .map(id -> "<@" + id + ">")
                .collect(Collectors.joining(", ", "**Already arranged their own games:** ", ""));
    }

    private String formatPairingsMessage(List<PairingResult> pairings) {
        var sb = new StringBuilder("**This week's Kill Team pairings:**\n\n");
        int matchNumber = 1;
        for (PairingResult pairing : pairings) {
            if (pairing.player2DiscordUserId() != null) {
                sb.append(matchNumber++).append(". ")
                        .append("<@").append(pairing.player1DiscordUserId()).append(">")
                        .append(" vs ")
                        .append("<@").append(pairing.player2DiscordUserId()).append(">")
                        .append("\n");
            } else {
                sb.append("\n").append("<@").append(pairing.player1DiscordUserId()).append(">")
                        .append(" has no explicit pairing. Feel free to look for your own :)");
            }
        }
        return sb.toString();
    }
}
