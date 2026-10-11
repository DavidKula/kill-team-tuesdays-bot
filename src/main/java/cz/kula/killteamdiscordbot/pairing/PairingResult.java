package cz.kula.killteamdiscordbot.pairing;

import org.jspecify.annotations.Nullable;

public record PairingResult(String player1DiscordUserId,
                            @Nullable String player2DiscordUserId,
                            @Nullable String tutorDiscordUserId) {

    public PairingResult(String player1DiscordUserId, @Nullable String player2DiscordUserId) {
        this(player1DiscordUserId, player2DiscordUserId, null);
    }
}
