package cz.kula.killteamdiscordbot.pairing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class PairingConfiguration {

    @Bean
    @ConditionalOnProperty(name = "pairing.strategy", havingValue = "random")
    PairingStrategy randomPairingStrategy() {
        return new RandomPairingStrategy();
    }

    @Bean
    @ConditionalOnProperty(name = "pairing.strategy", havingValue = "least-played", matchIfMissing = true)
    PairingStrategy leastPlayedPairingStrategy(PairingRepository pairingRepository) {
        return new LeastPlayedPairingStrategy(pairingRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "pairing.strategy", havingValue = "blossom")
    PairingStrategy blossomPairingStrategy(PairingRepository pairingRepository) {
        return new BlossomPairingStrategy(pairingRepository);
    }
}
