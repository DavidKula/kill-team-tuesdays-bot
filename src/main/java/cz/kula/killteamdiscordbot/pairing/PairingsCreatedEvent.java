package cz.kula.killteamdiscordbot.pairing;

import java.util.List;

public record PairingsCreatedEvent(Long pollId, String discordChannelId, List<PairingResult> pairings) {
}
