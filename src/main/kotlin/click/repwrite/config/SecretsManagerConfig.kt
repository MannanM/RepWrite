package click.repwrite.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient

@Configuration
class SecretsManagerConfig {

    @Value("\${aws.secrets-manager.region}")
    lateinit var region: String

    @Bean
    fun secretsManagerClient(): SecretsManagerClient =
        SecretsManagerClient.builder()
            .region(Region.of(region))
            .build()
}
