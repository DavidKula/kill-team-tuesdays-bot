package cz.kula.killteamdiscordbot.poll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PollVoteRepository extends JpaRepository<PollVote, Long> {

    Optional<PollVote> findByPollOptionIdAndDiscordUserId(Long pollOptionId, String discordUserId);

    void deleteByPollOptionIdAndDiscordUserId(Long pollOptionId, String discordUserId);
}
