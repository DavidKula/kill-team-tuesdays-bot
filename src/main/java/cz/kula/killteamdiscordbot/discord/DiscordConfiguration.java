package cz.kula.killteamdiscordbot.discord;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DiscordConfiguration {

    @Value("${discord.bot.token}")
    private String botToken;

    @Bean
    public JDA jda(PollVoteEventListener pollVoteEventListener) throws InterruptedException {
        return JDABuilder.createDefault(botToken)
                .enableIntents(GatewayIntent.GUILD_MESSAGE_POLLS, GatewayIntent.GUILD_MESSAGES)
                .addEventListeners(pollVoteEventListener)
                .build()
                .awaitReady();
    }
}
