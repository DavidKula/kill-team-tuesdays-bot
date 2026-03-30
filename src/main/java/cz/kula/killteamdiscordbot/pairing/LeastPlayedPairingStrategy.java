package cz.kula.killteamdiscordbot.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class LeastPlayedPairingStrategy implements PairingStrategy {

    private final PairingRepository pairingRepository;

    @Override
    public List<PairingResult> generatePairings(List<String> discordUserIds) {
        log.info("LeastPlayedPairingStrategy#generatePairings({})", discordUserIds);

        if (discordUserIds.size() < 2) {
            if (discordUserIds.size() == 1) {
                return List.of(new PairingResult(discordUserIds.getFirst(), null));
            }
            return List.of();
        }

        Map<String, Map<String, Integer>> matchCounts = buildMatchCountMap(discordUserIds);
        List<CandidatePair> candidates = buildCandidatePairs(discordUserIds, matchCounts);
        candidates.sort(Comparator.comparingInt(CandidatePair::count));

        return greedyMatch(candidates, discordUserIds);
    }

    private Map<String, Map<String, Integer>> buildMatchCountMap(List<String> playerIds) {
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        Set<String> playerSet = new HashSet<>(playerIds);

        List<Pairing> pastPairings = pairingRepository.findByPlayerIds(playerIds);
        for (Pairing pairing : pastPairings) {
            String p1 = pairing.getPlayer1DiscordUserId();
            String p2 = pairing.getPlayer2DiscordUserId();
            if (p2 == null || !playerSet.contains(p1) || !playerSet.contains(p2)) {
                continue;
            }
            counts.computeIfAbsent(p1, k -> new HashMap<>()).merge(p2, 1, Integer::sum);
            counts.computeIfAbsent(p2, k -> new HashMap<>()).merge(p1, 1, Integer::sum);
        }
        return counts;
    }

    private List<CandidatePair> buildCandidatePairs(List<String> playerIds, Map<String, Map<String, Integer>> matchCounts) {
        var candidates = new ArrayList<CandidatePair>();
        for (int i = 0; i < playerIds.size(); i++) {
            for (int j = i + 1; j < playerIds.size(); j++) {
                String p1 = playerIds.get(i);
                String p2 = playerIds.get(j);
                int count = matchCounts.getOrDefault(p1, Map.of()).getOrDefault(p2, 0);
                candidates.add(new CandidatePair(p1, p2, count));
            }
        }
        return candidates;
    }

    private List<PairingResult> greedyMatch(List<CandidatePair> sortedCandidates, List<String> playerIds) {
        Set<String> paired = new HashSet<>();
        var results = new ArrayList<PairingResult>();

        for (CandidatePair candidate : sortedCandidates) {
            if (paired.contains(candidate.player1()) || paired.contains(candidate.player2())) {
                continue;
            }
            results.add(new PairingResult(candidate.player1(), candidate.player2()));
            paired.add(candidate.player1());
            paired.add(candidate.player2());
        }

        // Handle odd player out with a bye
        for (String playerId : playerIds) {
            if (!paired.contains(playerId)) {
                results.add(new PairingResult(playerId, null));
            }
        }

        return results;
    }

    private record CandidatePair(String player1, String player2, int count) {
    }
}
