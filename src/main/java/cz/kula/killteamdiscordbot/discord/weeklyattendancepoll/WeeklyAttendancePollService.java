package cz.kula.killteamdiscordbot.discord.weeklyattendancepoll;

import cz.kula.killteamdiscordbot.discord.DiscordPollService;
import cz.kula.killteamdiscordbot.poll.Poll;
import cz.kula.killteamdiscordbot.poll.PollService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyAttendancePollService {

    private final PollService pollService;
    private final DiscordPollService discordPollService;

    @Value("${discord.channel.id}")
    private String channelId;

    @Value("${poll.question:Who's available to play Kill Team this tuesday at 17:30?}")
    private String pollQuestion;

    @Value("${poll.duration}")
    private Duration pollDuration;

    @Transactional
    public void createWeeklyPoll() {
        log.info("WeeklyAttendancePollService#createWeeklyPoll()");

        var sortedOptions = Arrays.stream(AttendanceOption.values())
                .sorted(Comparator.comparingInt(AttendanceOption::getIndex))
                .map(AttendanceOption::getLabel)
                .toList();

        Poll poll = pollService.createPoll(
                channelId,
                pollQuestion,
                sortedOptions,
                pollDuration
        );

        discordPollService.sendPoll(poll, pollDuration);
    }

}
