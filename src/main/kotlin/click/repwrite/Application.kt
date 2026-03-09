package click.repwrite

import click.repwrite.ai.GeminiProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication
@EnableConfigurationProperties(value = [GeminiProperties::class])
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
