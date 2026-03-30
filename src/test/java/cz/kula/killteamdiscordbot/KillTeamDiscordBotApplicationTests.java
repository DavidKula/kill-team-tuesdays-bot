package cz.kula.killteamdiscordbot;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "discord.bot.token=test-token",
        "discord.channel.id=123456789"
})
class KillTeamDiscordBotApplicationTests {

    @MockitoBean
    private JDA jda;

    @Test
    void contextLoads() {
    }
}
