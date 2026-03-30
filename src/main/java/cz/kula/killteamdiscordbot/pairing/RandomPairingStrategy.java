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
}
