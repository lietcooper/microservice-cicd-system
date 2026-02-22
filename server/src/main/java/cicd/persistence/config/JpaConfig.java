package cicd.persistence.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** Spring configuration for JPA entities and repositories. */
@Configuration
@EntityScan(basePackages = "cicd.persistence.entity")
@EnableJpaRepositories(basePackages = "cicd.persistence.repository")
public class JpaConfig {
}
