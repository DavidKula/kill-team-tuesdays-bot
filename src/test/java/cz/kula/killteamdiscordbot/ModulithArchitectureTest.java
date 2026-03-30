package cz.kula.killteamdiscordbot;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTest {

    @Test
    void verifyModulithStructure() {
        ApplicationModules.of(KillTeamDiscordBotApplication.class).verify();
    }
}
