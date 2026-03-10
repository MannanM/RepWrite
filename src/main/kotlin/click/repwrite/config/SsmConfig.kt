package click.repwrite.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ssm.SsmClient

@Configuration
class SsmConfig {

    @Value("\${aws.ssm.region}")
    lateinit var region: String

    @Bean
    fun ssmClient(): SsmClient =
        SsmClient.builder()
            .region(Region.of(region))
            .build()
}
