package cz.kula.killteamdiscordbot.pairing;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class RandomPairingStrategy implements PairingStrategy {

    @Override
    public List<PairingResult> generatePairings(List<String> discordUserIds) {
        log.info("RandomPairingStrategy#generatePairings({})", discordUserIds);
        var shuffled = new ArrayList<>(discordUserIds);
        Collections.shuffle(shuffled);
        var results = new ArrayList<PairingResult>();
        for (int i = 0; i < shuffled.size(); i += 2) {
            if (i + 1 < shuffled.size()) {
                results.add(new PairingResult(shuffled.get(i), shuffled.get(i + 1)));
            } else {
                results.add(new PairingResult(shuffled.get(i), null));
            }
        }
        return results;
    }

    @Override
    public BipartiteMatchResult generateBipartitePairings(List<String> groupA, List<String> groupB) {
        log.info("RandomPairingStrategy#generateBipartitePairings({}, {})", groupA, groupB);
        var shuffledA = new ArrayList<>(groupA);
        var shuffledB = new ArrayList<>(groupB);
        Collections.shuffle(shuffledA);
        Collections.shuffle(shuffledB);

        var pairings = new ArrayList<PairingResult>();
        int paired = Math.min(shuffledA.size(), shuffledB.size());
        for (int i = 0; i < paired; i++) {
            pairings.add(new PairingResult(shuffledA.get(i), shuffledB.get(i)));
        }
        var unmatched = new ArrayList<String>();
        unmatched.addAll(shuffledA.subList(paired, shuffledA.size()));
        unmatched.addAll(shuffledB.subList(paired, shuffledB.size()));
        return new BipartiteMatchResult(pairings, unmatched);
    }
}
