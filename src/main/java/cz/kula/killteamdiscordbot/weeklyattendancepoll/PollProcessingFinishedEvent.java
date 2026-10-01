package cz.kula.killteamdiscordbot.weeklyattendancepoll;

import cz.kula.killteamdiscordbot.pairing.PairingResult;

import java.util.List;

public record PollProcessingFinishedEvent(Long pollId,
                                          String discordChannelId,
                                          List<PairingResult> pairings,
                                          List<String> arrangedVoterDiscordUserIds) {
}
