package cz.kula.killteamdiscordbot.poll;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

    private final PollRepository pollRepository;
    private final PollOptionRepository pollOptionRepository;
    private final PollVoteRepository pollVoteRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Poll createPoll(String channelId, String question, List<String> optionTexts, Duration duration) {
        log.info("PollService#createPoll({}, {}, {}, {})", channelId, question, optionTexts, duration);
        OffsetDateTime now = OffsetDateTime.now();
        Poll poll = Poll.builder()
                .discordChannelId(channelId)
                .question(question)
                .createdAt(now)
                .expiresAt(now.plus(duration))
                .build();

        for (int i = 0; i < optionTexts.size(); i++) {
            PollOption option = PollOption.builder()
                    .poll(poll)
                    .optionText(optionTexts.get(i))
                    .optionIndex(i + 1) // in discord the poll options are starting at 1 (not 0)
                    .build();
            poll.getOptions().add(option);
        }

        return pollRepository.save(poll);
    }

    @Transactional
    public void updateDiscordMessageId(Long pollId, String discordMessageId) {
        log.info("PollService#updateDiscordMessageId({}, {})", pollId, discordMessageId);
        pollRepository.findById(pollId).ifPresentOrElse(poll -> {
                    poll.setDiscordMessageId(discordMessageId);
                    poll.setState(PollState.RUNNING);
                    pollRepository.save(poll);
                    log.info("Poll {} successfully moved to RUNNING.", pollId);
                },
                () -> {
                    log.error("Failed to find poll in db( pollId: {}, discordMessageId: {})!", pollId, discordMessageId);
                    throw new RuntimeException("Failed to make the poll running! Poll not found!");
                }
        );

    }

    @Transactional
    public void closePoll(String messageId) {
        log.info("PollService#closePoll({})", messageId);
        pollRepository.findByDiscordMessageId(messageId).ifPresentOrElse(poll -> {
                    poll.setState(PollState.CLOSED);
                    pollRepository.save(poll);
                    eventPublisher.publishEvent(new PollClosedEvent(poll.getId()));
                },
                () -> {
                    log.error("Unable to close poll {}, not present in db!)", messageId);
                    throw new RuntimeException("Unable to close poll %s, it is not present in db.".formatted(messageId));
                }
        );
    }

    @Transactional
    public void recordVote(String messageId, long answerId, String userId) {
        log.info("PollService#recordVote({}, {}, {})", messageId, answerId, userId);
        pollOptionRepository.findByPollDiscordMessageIdAndOptionIndex(messageId, (int) answerId)
                .ifPresentOrElse(option -> {
                            if (pollVoteRepository.findByPollOptionIdAndDiscordUserId(option.getId(), userId).isEmpty()) {
                                PollVote vote = PollVote.builder()
                                        .pollOption(option)
                                        .discordUserId(userId)
                                        .votedAt(OffsetDateTime.now())
                                        .build();
                                pollVoteRepository.save(vote);
                                log.info("Recorded vote: user={} option={}", userId, option.getOptionText());
                            } else {
                                log.error("User already voted before and didn't remove its vote. {}, {}, {}", messageId, answerId, userId);
                            }
                        },
                        () -> log.error("Poll option not found! {}, {}, {}", messageId, answerId, userId)
                );
    }

    @Transactional
    public void removeVote(String messageId, long answerId, String userId) {
        log.info("PollService#removeVote({}, {}, {})", messageId, answerId, userId);
        pollOptionRepository.findByPollDiscordMessageIdAndOptionIndex(messageId, (int) answerId)
                .ifPresentOrElse(option -> {
                            pollVoteRepository.deleteByPollOptionIdAndDiscordUserId(option.getId(), userId);
                            log.info("Removed vote: user={} option={}", userId, option.getOptionText());
                        },
                        () -> log.error("Poll option not found! {}, {}, {}", messageId, answerId, userId)
                );
    }

    @Transactional(readOnly = true)
    public Poll getPoll(Long pollId) {
        return pollRepository.findById(pollId)
                .orElseThrow(() -> new IllegalArgumentException("Poll not found: " + pollId));
    }

    @Transactional(readOnly = true)
    public List<String> getVoterDiscordUserIdsByOptionIndex(Long pollId, int optionIndex) {
        log.info("PollService#getVoterDiscordUserIdsByOptionIndex({}, {})", pollId, optionIndex);
        return pollRepository.findById(pollId)
                .map(poll -> poll.getOptions().stream()
                        .filter(option -> option.getOptionIndex() == optionIndex)
                        .flatMap(option -> option.getVotes().stream())
                        .map(PollVote::getDiscordUserId)
                        .toList())
                .orElse(List.of());
    }
}
