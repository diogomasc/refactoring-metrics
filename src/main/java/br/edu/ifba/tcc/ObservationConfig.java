package br.edu.ifba.tcc;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita o processamento de @Observed via AOP.
 *
 * O ObservedAspect intercepta todos os métodos públicos de beans anotados
 * com @Observed e cria uma Observation para cada invocação, registrando:
 *   - duração do método (Histogram → p50/p95/p99 no Prometheus)
 *   - profundidade de pilha de chamadas (via contexto propagado)
 *   - tags de erro em caso de exceção
 *
 * Sem este bean, @Observed não tem efeito mesmo com spring-boot-starter-aop
 * no classpath — o Spring Boot não registra ObservedAspect automaticamente.
 */
@Configuration(proxyBeanMethods = false)
public class ObservationConfig {

    @Bean
    ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }
}
