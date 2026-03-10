package click.repwrite.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "repwrite.admin")
data class RepWriteProperties(
    var password: String? = null
)
