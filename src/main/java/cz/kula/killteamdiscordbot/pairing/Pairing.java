package cz.kula.killteamdiscordbot.pairing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

@Entity
@Table(name = "pairings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pairing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "poll_id", nullable = false)
    private Long pollId;

    @Column(name = "player1_discord_user_id", nullable = false)
    private String player1DiscordUserId;

    @Column(name = "player2_discord_user_id")
    private @Nullable String player2DiscordUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
