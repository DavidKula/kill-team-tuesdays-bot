package cz.kula.killteamdiscordbot.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Contract;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PairingService {

    private final PairingRepository pairingRepository;
    private final PairingStrategy pairingStrategy;

    @Transactional
    public List<Pairing> createPairings(Long pollId,
                                        List<String> yesVoterDiscordUserIds,
                                        List<String> learningVoterDiscordUserIds,
                                        List<String> teachingVoterDiscordUserIds) {
        log.info("PairingService#createPairings({}, yes={}, learning={}, teaching={})",
                pollId, yesVoterDiscordUserIds, learningVoterDiscordUserIds, teachingVoterDiscordUserIds);

        int totalVoters = yesVoterDiscordUserIds.size()
                + learningVoterDiscordUserIds.size()
                + teachingVoterDiscordUserIds.size();
        if (totalVoters < 2) {
            log.info("Not enough players to create pairings: {}", totalVoters);
            return List.of();
        }

        var learners = new ArrayList<>(learningVoterDiscordUserIds);
        var tutors = new ArrayList<>(teachingVoterDiscordUserIds);
        var allResults = new ArrayList<PairingResult>();
        if (totalVoters % 2 != 0 && learners.size() >= 2 && !tutors.isEmpty()) {
            allResults.add(pickTutoredTrio(learners, tutors));
        }

        BipartiteMatchResult bipartite = pairingStrategy.generateBipartitePairings(learners, tutors);

        var remainingPool = new ArrayList<String>(yesVoterDiscordUserIds.size() + bipartite.unmatched().size());
        remainingPool.addAll(yesVoterDiscordUserIds);
        remainingPool.addAll(bipartite.unmatched());

        List<PairingResult> remainingPairings = remainingPool.size() >= 2
                ? pairingStrategy.generatePairings(remainingPool)
                : remainingPool.stream().map(id -> new PairingResult(id, null)).toList();

        allResults.addAll(bipartite.pairings());
        allResults.addAll(remainingPairings);

        var now = OffsetDateTime.now();
        var pairings = allResults.stream()
                .map(result -> Pairing.builder()
                        .pollId(pollId)
                        .player1DiscordUserId(result.player1DiscordUserId())
                        .player2DiscordUserId(result.player2DiscordUserId())
                        .tutorDiscordUserId(result.tutorDiscordUserId())
                        .createdAt(now)
                        .build())
                .toList();
        return pairingRepository.saveAll(pairings);
    }

    @Contract(mutates = "param1, param2")
    private PairingResult pickTutoredTrio(List<String> learners, List<String> tutors) {
        Collections.shuffle(learners);
        Collections.shuffle(tutors);
        var trio = new PairingResult(learners.removeFirst(), learners.removeFirst(), tutors.removeFirst());
        log.info("Tutored trio: {}", trio);
        return trio;
    }
}
