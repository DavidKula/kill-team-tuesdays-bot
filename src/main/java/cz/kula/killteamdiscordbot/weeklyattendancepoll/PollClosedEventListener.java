package cz.kula.killteamdiscordbot.weeklyattendancepoll;

import cz.kula.killteamdiscordbot.pairing.PairingResult;
import cz.kula.killteamdiscordbot.pairing.PairingService;
import cz.kula.killteamdiscordbot.pairing.PairingsCreatedEvent;
import cz.kula.killteamdiscordbot.poll.PollClosedEvent;
import cz.kula.killteamdiscordbot.poll.PollService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PollClosedEventListener {

    private final PollService pollService;
    private final PairingService pairingService;
    private final ApplicationEventPublisher eventPublisher;

//    @ApplicationModuleListener
    public void on(PollClosedEvent event) {
        log.info("PollClosedEventListener#on({})", event);
        var poll = pollService.getPoll(event.pollId());
        var yesVoters = pollService.getVoterDiscordUserIdsByOptionIndex(event.pollId(), AttendanceOption.YES.getIndex());
        var learningVoters = pollService.getVoterDiscordUserIdsByOptionIndex(event.pollId(), AttendanceOption.YES_LEARNING.getIndex());
        var teachingVoters = pollService.getVoterDiscordUserIdsByOptionIndex(event.pollId(), AttendanceOption.YES_TEACHING.getIndex());
        var pairings = pairingService.createPairings(event.pollId(), yesVoters, learningVoters, teachingVoters);
        if (!pairings.isEmpty()) {
            var pairingResults = pairings.stream()
                    .map(p -> new PairingResult(p.getPlayer1DiscordUserId(), p.getPlayer2DiscordUserId()))
                    .toList();
            eventPublisher.publishEvent(new PairingsCreatedEvent(event.pollId(), poll.getDiscordChannelId(), pairingResults));
        }
    }
}
