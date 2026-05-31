package cz.kula.killteamdiscordbot.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
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

        BipartiteMatchResult bipartite = pairingStrategy.generateBipartitePairings(
                learningVoterDiscordUserIds, teachingVoterDiscordUserIds);

        var remainingPool = new ArrayList<String>(yesVoterDiscordUserIds.size() + bipartite.unmatched().size());
        remainingPool.addAll(yesVoterDiscordUserIds);
        remainingPool.addAll(bipartite.unmatched());

        List<PairingResult> remainingPairings = remainingPool.size() >= 2
                ? pairingStrategy.generatePairings(remainingPool)
                : remainingPool.stream().map(id -> new PairingResult(id, null)).toList();

        var allResults = new ArrayList<PairingResult>(bipartite.pairings().size() + remainingPairings.size());
        allResults.addAll(bipartite.pairings());
        allResults.addAll(remainingPairings);

        var now = OffsetDateTime.now();
        var pairings = allResults.stream()
                .map(result -> Pairing.builder()
                        .pollId(pollId)
                        .player1DiscordUserId(result.player1DiscordUserId())
                        .player2DiscordUserId(result.player2DiscordUserId())
                        .createdAt(now)
                        .build())
                .toList();
        return pairingRepository.saveAll(pairings);
    }
}
