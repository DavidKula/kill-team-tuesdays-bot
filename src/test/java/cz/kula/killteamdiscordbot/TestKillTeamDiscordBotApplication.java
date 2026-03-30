package cz.kula.killteamdiscordbot;

import org.springframework.boot.SpringApplication;

public class TestKillTeamDiscordBotApplication {

    public static void main(String[] args) {
        SpringApplication.from(KillTeamDiscordBotApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
