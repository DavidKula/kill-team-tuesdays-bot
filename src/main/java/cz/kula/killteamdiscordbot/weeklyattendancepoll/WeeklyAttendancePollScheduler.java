package cz.kula.killteamdiscordbot.weeklyattendancepoll;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyAttendancePollScheduler {

    private final WeeklyAttendancePollService weeklyAttendancePollService;

    @Scheduled(cron = "${poll.cron}")
    public void createWeeklyPoll() {
        log.info("WeeklyAttendancePollScheduler#createWeeklyPoll()");
        weeklyAttendancePollService.createWeeklyPoll();
    }
}
