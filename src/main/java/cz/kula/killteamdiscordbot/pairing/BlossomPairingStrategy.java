package cz.kula.killteamdiscordbot.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.alg.matching.blossom.v5.KolmogorovWeightedPerfectMatching;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
public class BlossomPairingStrategy implements PairingStrategy {

    private static final int COUNT_WEIGHT = 1000;
    private static final int RECENCY_LAST_WEEK = 1500; // <= 8 days — above the count tier
    private static final int RECENCY_TWO_WEEKS = 15;   // <= 15 days — tie-breaker
    private static final int RECENCY_MONTH = 5;        // <= 36 days — tie-breaker
    private static final String DUMMY_PREFIX = "__BYE";
    private static final String BYE_TOKEN = DUMMY_PREFIX + "__";

    private final PairingRepository pairingRepository;

    @Override
    public List<PairingResult> generatePairings(List<String> discordUserIds) {
        log.info("BlossomPairingStrategy#generatePairings({})", discordUserIds);

        if (discordUserIds.isEmpty()) {
            return List.of();
        }
        if (discordUserIds.size() == 1) {
            return List.of(new PairingResult(discordUserIds.getFirst(), null));
        }
        if (discordUserIds.size() == 2) {
            return List.of(new PairingResult(discordUserIds.get(0), discordUserIds.get(1)));
        }

        OffsetDateTime now = OffsetDateTime.now();
        Map<PairKey, PairStats> stats = buildPerPairStats(discordUserIds);

        List<String> nodes = new ArrayList<>(new LinkedHashSet<>(discordUserIds));
        if (nodes.size() % 2 != 0) {
            nodes.add(BYE_TOKEN);
        }

        var graph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        for (String node : nodes) {
            graph.addVertex(node);
        }
        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                String a = nodes.get(i);
                String b = nodes.get(j);
                DefaultWeightedEdge edge = graph.addEdge(a, b);
                double weight = (isDummy(a) || isDummy(b)) ? 0.0 : computeWeight(a, b, stats, now);
                graph.setEdgeWeight(edge, weight);
            }
        }

        var matching = new KolmogorovWeightedPerfectMatching<>(graph).getMatching();

        var results = new ArrayList<PairingResult>();
        for (DefaultWeightedEdge edge : matching.getEdges()) {
            String a = graph.getEdgeSource(edge);
            String b = graph.getEdgeTarget(edge);
            if (isDummy(a)) {
                results.add(new PairingResult(b, null));
            } else if (isDummy(b)) {
                results.add(new PairingResult(a, null));
            } else {
                results.add(new PairingResult(a, b));
            }
        }
        return results;
    }

    @Override
    public BipartiteMatchResult generateBipartitePairings(List<String> groupA, List<String> groupB) {
        log.info("BlossomPairingStrategy#generateBipartitePairings({}, {})", groupA, groupB);

        if (groupA.isEmpty() || groupB.isEmpty()) {
            var unmatched = Stream.concat(groupA.stream(), groupB.stream()).toList();
            return new BipartiteMatchResult(List.of(), unmatched);
        }

        // groupA (learners) and groupB (teachers) come from distinct single-answer poll
        // options, so in practice they are disjoint and duplicate-free. De-duplicate each
        // side defensively to keep the simple graph well-formed (duplicate vertices would
        // otherwise produce a null/duplicate edge). A player appearing in BOTH groups is
        // contradictory input and is not handled here.
        List<String> learners = new ArrayList<>(new LinkedHashSet<>(groupA));
        List<String> teachers = new ArrayList<>(new LinkedHashSet<>(groupB));

        List<String> allPlayers = Stream.concat(learners.stream(), teachers.stream()).toList();
        Map<PairKey, PairStats> stats = buildPerPairStats(allPlayers);
        OffsetDateTime now = OffsetDateTime.now();

        List<String> paddedA = new ArrayList<>(learners);
        List<String> paddedB = new ArrayList<>(teachers);
        int diff = learners.size() - teachers.size();
        if (diff > 0) {
            for (int i = 0; i < diff; i++) {
                paddedB.add(DUMMY_PREFIX + "_B_" + i + "__");
            }
        } else if (diff < 0) {
            for (int i = 0; i < -diff; i++) {
                paddedA.add(DUMMY_PREFIX + "_A_" + i + "__");
            }
        }

        var graph = new SimpleWeightedGraph<String, DefaultWeightedEdge>(DefaultWeightedEdge.class);
        for (String a : paddedA) {
            graph.addVertex(a);
        }
        for (String b : paddedB) {
            graph.addVertex(b);
        }
        for (String a : paddedA) {
            for (String b : paddedB) {
                DefaultWeightedEdge edge = graph.addEdge(a, b);
                double weight = (isDummy(a) || isDummy(b)) ? 0.0 : computeWeight(a, b, stats, now);
                graph.setEdgeWeight(edge, weight);
            }
        }

        var matching = new KolmogorovWeightedPerfectMatching<>(graph).getMatching();

        Set<String> aSide = new HashSet<>(paddedA);
        var pairings = new ArrayList<PairingResult>();
        var unmatched = new ArrayList<String>();
        for (DefaultWeightedEdge edge : matching.getEdges()) {
            String source = graph.getEdgeSource(edge);
            String target = graph.getEdgeTarget(edge);
            String learner = aSide.contains(source) ? source : target;
            String teacher = learner.equals(source) ? target : source;

            if (isDummy(learner) && isDummy(teacher)) {
                continue;
            } else if (isDummy(teacher)) {
                unmatched.add(learner);
            } else if (isDummy(learner)) {
                unmatched.add(teacher);
            } else {
                pairings.add(new PairingResult(learner, teacher));
            }
        }
        return new BipartiteMatchResult(pairings, unmatched);
    }

    private Map<PairKey, PairStats> buildPerPairStats(List<String> playerIds) {
        Set<String> playerSet = new HashSet<>(playerIds);
        Map<PairKey, PairStats> stats = new HashMap<>();
        for (Pairing pairing : pairingRepository.findByPlayerIds(playerIds)) {
            String p1 = pairing.getPlayer1DiscordUserId();
            String p2 = pairing.getPlayer2DiscordUserId();
            if (p2 == null || !playerSet.contains(p1) || !playerSet.contains(p2)) {
                continue;
            }
            PairKey key = new PairKey(p1, p2);
            stats.merge(
                    key,
                    new PairStats(1, pairing.getCreatedAt()),
                    (existing, incoming) -> new PairStats(
                            existing.count() + incoming.count(),
                            incoming.lastPlayedAt().isAfter(existing.lastPlayedAt())
                                    ? incoming.lastPlayedAt()
                                    : existing.lastPlayedAt()));
        }
        return stats;
    }

    private double computeWeight(String a, String b, Map<PairKey, PairStats> stats, OffsetDateTime now) {
        PairStats s = stats.get(new PairKey(a, b));
        if (s == null) {
            return 0.0;
        }
        return (double) s.count() * COUNT_WEIGHT + recencyPenalty(s.lastPlayedAt(), now);
    }

    private int recencyPenalty(OffsetDateTime lastPlayedAt, OffsetDateTime now) {
        long daysSince = ChronoUnit.DAYS.between(lastPlayedAt, now);
        if (daysSince <= 8) {
            return RECENCY_LAST_WEEK;
        }
        if (daysSince <= 15) {
            return RECENCY_TWO_WEEKS;
        }
        if (daysSince <= 36) {
            return RECENCY_MONTH;
        }
        return 0;
    }

    private boolean isDummy(String node) {
        return node.startsWith(DUMMY_PREFIX);
    }

    private record PairKey(String a, String b) {
        private PairKey(String a, String b) {
            if (a.compareTo(b) <= 0) {
                this.a = a;
                this.b = b;
            } else {
                this.a = b;
                this.b = a;
            }
        }
    }

    private record PairStats(int count, OffsetDateTime lastPlayedAt) {
    }
}
