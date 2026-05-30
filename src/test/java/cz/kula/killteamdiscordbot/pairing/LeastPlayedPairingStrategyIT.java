package cz.kula.killteamdiscordbot.pairing;

import cz.kula.killteamdiscordbot.poll.PollClosedEvent;
import cz.kula.killteamdiscordbot.poll.PollService;
import cz.kula.killteamdiscordbot.weeklyattendancepoll.PairingsMessageService;
import cz.kula.killteamdiscordbot.weeklyattendancepoll.PollClosedEventListener;
import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ActiveProfiles("test")
@Import(DumpInitTestcontainersConfiguration.class)
class LeastPlayedPairingStrategyIT {

    @Autowired
    private PairingRepository pairingRepository;

    @Autowired
    private PollClosedEventListener eventListener;

    @Autowired
    private PollService pollService;

    @MockitoBean
    private PairingsMessageService pairingsMessageService;

    @MockitoBean
    private JDA jda;

    @Test
    @Disabled
    void testPairings_bipartite() {

        pollService.recordVote("1507337341944201276", 3, "user1");
        pollService.recordVote("1507337341944201276", 3, "user2");
        pollService.recordVote("1507337341944201276", 4, "user3");

        final long pollId = 9;
        var pairingsAfter = getPairings();

        pairingRepository.deleteAllByIdInBatch(List.of(
                31L, 32L, 33L, 34L, 35L, 36L, 37L
        ));

        var pairingsBefore = getPairings();

        System.out.println(pairingsBefore);
        System.out.println("****************/*****************");
        System.out.println(pairingsAfter);

        doNothing().when(pairingsMessageService).on(any());

        System.out.println(pairingRepository.findAll().size());
        eventListener.on(new PollClosedEvent(pollId));
        System.out.println(pairingRepository.findAll().size());

        var finalPairings = getPairings();
        System.out.println("****************/*****************");
        System.out.println(finalPairings);

        System.out.println("****************/*****************");

        validateDuplicates(pairingsBefore);
        System.out.println("****************/*****************");
        validateDuplicates(pairingsAfter);
        System.out.println("****************/*****************");
        validateDuplicates(finalPairings);

        System.out.println("****************/*****************");

        System.out.println();
        System.out.println();
    }

    @Test
    @Disabled
    void testPairings() {
        final long pollId = 9;
        var pairingsAfter = getPairings();

        pairingRepository.deleteAllByIdInBatch(List.of(
                31L, 32L, 33L, 34L, 35L, 36L, 37L
        ));

        var pairingsBefore = getPairings();

        System.out.println(pairingsBefore);
        System.out.println("****************/*****************");
        System.out.println(pairingsAfter);

        doNothing().when(pairingsMessageService).on(any());

        System.out.println(pairingRepository.findAll().size());
        eventListener.on(new PollClosedEvent(pollId));
        System.out.println(pairingRepository.findAll().size());

        var finalPairings = getPairings();
        System.out.println("****************/*****************");
        System.out.println(finalPairings);

        System.out.println("****************/*****************");

        validateDuplicates(pairingsBefore);
        System.out.println("****************/*****************");
        validateDuplicates(pairingsAfter);
        System.out.println("****************/*****************");
        validateDuplicates(finalPairings);

        System.out.println("****************/*****************");

        System.out.println();
        System.out.println();
    }

    private void validateDuplicates(Map<String, List<String>> opponentsByPlayer) {
        opponentsByPlayer.forEach((key, value) -> {
            var set = new HashSet<>();
            value.forEach(opponent -> {
                if (!set.add(opponent)) {
                    System.out.printf("Player %s has duplicate opponent %s.\n", key, opponent);
                }
            });
        });
    }

    private Map<String, List<String>> getPairings() {
        var pairings = pairingRepository.findAll();

        Map<String, List<String>> allPairings = pairings.stream()
                .collect(
                        Collectors.toMap(
                                Pairing::getPlayer1DiscordUserId,
                                pairing -> {
                                    var list = new ArrayList<String>();
                                    list.add(pairing.getPlayer2DiscordUserId());
                                    return list;
                                },
                                (v1, v2) -> {
                                    v1.addAll(v2);
                                    return v1;
                                }
                        )
                );

        Map<String, List<String>> pairingsByPlayer2 = pairings.stream()
                .collect(
                        Collectors.toMap(
                                Pairing::getPlayer2DiscordUserId,
                                pairing -> {
                                    var list = new ArrayList<String>();
                                    list.add(pairing.getPlayer1DiscordUserId());
                                    return list;
                                },
                                (v1, v2) -> {
                                    v1.addAll(v2);
                                    return v1;
                                }
                        )
                );

        allPairings.putAll(pairingsByPlayer2);
        allPairings.values().forEach(v -> v.sort(String::compareTo));

        return allPairings;
    }

}
