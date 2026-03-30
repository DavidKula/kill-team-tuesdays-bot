package cz.kula.killteamdiscordbot.poll;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PollOptionRepository extends JpaRepository<PollOption, Long> {

    Optional<PollOption> findByPollDiscordMessageIdAndOptionIndex(String discordMessageId, int optionIndex);
}
