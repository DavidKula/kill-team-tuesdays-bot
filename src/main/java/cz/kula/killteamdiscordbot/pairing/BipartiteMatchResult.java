package cz.kula.killteamdiscordbot.pairing;

import java.util.List;

public record BipartiteMatchResult(List<PairingResult> pairings, List<String> unmatched) {
}
