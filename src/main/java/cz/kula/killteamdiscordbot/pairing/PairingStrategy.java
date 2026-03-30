package cz.kula.killteamdiscordbot.pairing;

import java.util.List;

public interface PairingStrategy {

    List<PairingResult> generatePairings(List<String> discordUserIds);
}
