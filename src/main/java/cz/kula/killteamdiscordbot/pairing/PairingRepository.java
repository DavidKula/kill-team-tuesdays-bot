package cz.kula.killteamdiscordbot.pairing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PairingRepository extends JpaRepository<Pairing, Long> {

    List<Pairing> findByPollId(Long pollId);

    @Query("SELECT p FROM Pairing p WHERE p.player1DiscordUserId IN :playerIds OR p.player2DiscordUserId IN :playerIds")
    List<Pairing> findByPlayerIds(@Param("playerIds") List<String> playerIds);
}
