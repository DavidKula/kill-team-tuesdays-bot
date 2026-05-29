package cz.kula.killteamdiscordbot;

import net.dv8tion.jda.api.JDA;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class KillTeamDiscordBotApplicationTests {

    @MockitoBean
    private JDA jda;

    @Test
    void contextLoads() {
    }
}
