package cz.kula.killteamdiscordbot.pairing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PairingService {

    private final PairingRepository pairingRepository;
    private final PairingStrategy pairingStrategy;

    @Transactional
    public List<Pairing> createPairings(Long pollId, List<String> yesVoterDiscordUserIds) {
        log.info("PairingService#createPairings({}, {})", pollId, yesVoterDiscordUserIds);
        if (yesVoterDiscordUserIds.size() < 2) {
            log.info("Not enough players to create pairings: {}", yesVoterDiscordUserIds.size());
            return List.of();
        }
        var pairingResults = pairingStrategy.generatePairings(yesVoterDiscordUserIds);
        var now = OffsetDateTime.now();
        var pairings = pairingResults.stream()
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
