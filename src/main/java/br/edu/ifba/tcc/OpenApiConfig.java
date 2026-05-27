package br.edu.ifba.tcc;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configura os metadados globais da especificação OpenAPI 3.1
 * acessível em /api-docs e /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI openAPI() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("TCC Protótipo")
                                                .version("1.0.0")
                                                .description("""
                                                                Protótipo desenvolvido para o TCC de BSI – IFBA.

                                                                Demonstra a coleta de **métricas estáticas** (SonarQube) e
                                                                **métricas dinâmicas** (Micrometer → Prometheus → Grafana) antes e
                                                                após refatoração de _code smells_ intencionais.

                                                                ### Infraestrutura de observabilidade
                                                                - **Prometheus**: `http://localhost:9090`
                                                                - **Grafana**: `http://localhost:3000`
                                                                - **SonarQube**: `http://localhost:9000`
                                                                - **Actuator**: `http://localhost:8080/actuator`
                                                                """)
                                                .contact(new Contact()
                                                                .name("Diogo Mascarenhas")
                                                                .url("https://github.com/diogomasc"))
                                                .license(new License()
                                                                .name("MIT")
                                                                .url("https://opensource.org/licenses/MIT")));
        }
}
