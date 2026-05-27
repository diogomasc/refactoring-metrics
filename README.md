# refactoring-metrics

**Protótipo Java para o TCC**
**Mitigação de Débito Técnico Estrutural: Uma Abordagem Híbrida de Refatoração Baseada em Observabilidade e Métricas de Qualidade**

Autor: Diogo Mascarenhas — Sistemas de Informação, IFBA

---

## Visão Geral

Este repositório é o **único ponto de entrada** do TCC: contém o protótipo Spring Boot e toda a stack de observabilidade/análise necessária para coletar métricas antes e após a refatoração.

- O **protótipo Java** roda diretamente no host (`mvn spring-boot:run`)
- A **infra de observabilidade** roda em Docker (`infra/docker-compose.infra.yml`)

---

## Estrutura do Projeto

```
refactoring-metrics/
├── pom.xml                                        # Dependências + plugins (Sonar, JaCoCo)
├── README.md                                      # Este arquivo
│
├── infra/                                         # Stack de observabilidade (Docker)
│   ├── docker-compose.infra.yml                   # SonarQube, Postgres, Prometheus, Grafana, K6
│   ├── prometheus.yml                             # Scrape config (host.docker.internal:8080)
│   ├── k6/
│   │   └── load-test.js                           # Script de carga (baseline e pós-refatoração)
│   └── grafana/
│       └── provisioning/
│           └── datasources/
│               └── prometheus.yml                 # Datasource pré-provisionado
│
└── src/
    ├── main/
    │   ├── java/br/edu/ifba/tcc/
    │   │   ├── TccPrototipoApplication.java        # Entry point Spring Boot
    │   │   └── OpenApiConfig.java                  # Metadados Swagger UI
    │   └── resources/
    │       └── application.properties              # Config JPA/H2, Actuator, Swagger
    └── test/
        └── java/br/edu/ifba/tcc/
            └── TccPrototipoApplicationTests.java
```

---

## Containers da Infra

| Container | Imagem | Porta | Função |
|---|---|---|---|
| `tcc-sonarqube` | `sonarqube:community` | 9000 | Análise estática: CC, CBO, LCOM, TDR, code smells |
| `tcc-postgres` | `postgres:15-alpine` | 5432 | Banco de dados do SonarQube |
| `tcc-prometheus` | `prom/prometheus:latest` | 9090 | Coleta `/actuator/prometheus` a cada 5s |
| `tcc-grafana` | `grafana/grafana:latest` | 3000 | Dashboards de latência, throughput e taxa de erro |
| `tcc-k6` | `grafana/k6:latest` | — | Testes de carga (sob demanda, profile `testing`) |

---

## Pré-requisitos

- **Java 21** instalado no host
- **Maven 3.9+** instalado no host
- **Docker Engine** (Linux) ou **Docker Desktop** (Windows/macOS)
- **`vm.max_map_count ≥ 524288`** no host (exigido pelo Elasticsearch do SonarQube — ver [Troubleshooting](#troubleshooting))

---

## Subindo o Ambiente

### 1. Stack de infra (Docker)

```bash
# A partir da raiz do projeto
docker compose -f infra/docker-compose.infra.yml up -d
```

Aguarde ~60 segundos para o SonarQube inicializar (Elasticsearch embutido).

### 2. Protótipo Spring Boot (host)

```bash
mvn spring-boot:run
```

### 3. Verificar que tudo está funcionando

```bash
# Prometheus coletando o Spring Boot?
# → http://localhost:9090/targets  (job spring-boot-prototipo deve estar UP)

# Actuator respondendo?
curl http://localhost:8080/actuator/health

# Swagger UI disponível?
# → http://localhost:8080/swagger-ui.html
```

### Parar tudo

```bash
# Parar Spring Boot: Ctrl+C no terminal do mvn spring-boot:run

# Parar infra
docker compose -f infra/docker-compose.infra.yml down
```

---

## URLs de Acesso

| URL | Descrição | Credenciais |
|---|---|---|
| `http://localhost:8080/swagger-ui.html` | Swagger UI — documentação interativa | — |
| `http://localhost:8080/api-docs` | Especificação OpenAPI 3.1 (JSON) | — |
| `http://localhost:8080/actuator/health` | Health check | — |
| `http://localhost:8080/actuator/prometheus` | Métricas no formato Prometheus | — |
| `http://localhost:9000` | SonarQube | `admin` / `admin` ¹ |
| `http://localhost:9090` | Prometheus | — |
| `http://localhost:9090/targets` | Status dos scrapes | — |
| `http://localhost:3000` | Grafana | `admin` / `admin` |

> ¹ O SonarQube exige troca de senha no primeiro acesso.

---

## Análise Estática (SonarQube)

```bash
# 1. Gere o token em: http://localhost:9000 → My Account → Security → Generate Token

# 2. Rode com testes + cobertura JaCoCo + análise Sonar
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=tcc-prototipo \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=<seu-token>
```

> `sonar.projectKey` e `sonar.host.url` já estão definidos no `pom.xml`. Apenas o token é necessário em tempo de execução.

---

## Testes de Carga (K6)

```bash
docker compose -f infra/docker-compose.infra.yml \
  --profile testing run --rm k6 run /scripts/load-test.js
```

Edite `infra/k6/load-test.js` para adicionar os endpoints do protótipo.
**Use o mesmo perfil de carga nas fases baseline e pós-refatoração** para garantir comparabilidade.

### Perfil de carga configurado

| Etapa | Duração | VUs | Propósito |
|---|---|---|---|
| Ramp-up | 30s | 0 → 10 | Aquecimento gradual |
| Sustentada | 2min | 10 | Comportamento em carga normal |
| Pico | 30s | 10 → 50 | Simula spike de tráfego |
| Carga de pico | 1min | 50 | Comportamento sob estresse |
| Ramp-down | 30s | 50 → 0 | Recuperação |

**Thresholds:** `p95 < 2s` · `taxa de erro < 5%`

---

## Métricas Coletadas

### Code Smells — Detecção Estática (SonarQube)

| Code Smell | Problema Central |
|---|---|
| God Class | Classe com múltiplas responsabilidades, alta complexidade |
| Long Method | Método muito longo, alta complexidade ciclomática |
| Feature Envy | Método usa mais dados de outra classe do que da própria |
| Shotgun Surgery | Mudança simples exige alteração em muitas classes |
| Dispersed Coupling | Chamadas espalhadas por muitos módulos |
| Data Class | Classe só com getters/setters, sem comportamento |

### Métricas Estáticas (SonarQube)

| Métrica | Sigla | O que mede |
|---|---|---|
| Complexidade Ciclomática | CC / WMC | Caminhos independentes de execução por método/classe |
| Acoplamento Aferente | Ca / Fan-In | Quantas classes dependem desta classe |
| Acoplamento Eferente | Ce / Fan-Out | De quantas classes esta classe depende |
| Coupling Between Objects | CBO | Total de classes acopladas (Ca + Ce) |
| Lack of Cohesion of Methods | LCOM | Ausência de coesão — God Class costuma ter LCOM alto |
| Technical Debt Ratio | TDR | Estimativa de tempo para remediar o débito |
| Lines of Code | LOC | Tamanho da classe/método |
| Response for Class | RFC | Métodos invocáveis em resposta a uma mensagem |

### Métricas Dinâmicas (Micrometer → Prometheus → Grafana)

| Métrica | Como é coletada |
|---|---|
| Latência por endpoint (p50/p95/p99) | `http.server.requests` — automático via Actuator |
| Taxa de erro (%) | tag `outcome=SERVER_ERROR` em `http.server.requests` |
| Throughput (req/s) | `http_reqs` do K6 |
| Tempo de execução por método | `@Observed` nos métodos de serviço |
| Conexões JDBC | `jdbc.connections.*` — automático via HikariCP |

---

## Stack Tecnológica do Protótipo

| Componente | Versão | Função |
|---|---|---|
| Java | 21 (LTS) | Runtime |
| Spring Boot | 4.x | Framework principal |
| Spring Data JPA + H2 | — | Persistência em memória |
| Spring Boot Actuator | — | `/actuator/health`, `/actuator/prometheus` |
| Micrometer + Prometheus Registry | 1.x | Exportação de métricas |
| SpringDoc OpenAPI | 2.8.x | Swagger UI (`/swagger-ui.html`) |
| Lombok | 1.18.x | Redução de boilerplate |
| JaCoCo | 0.8.x | Cobertura de testes (consumida pelo SonarQube) |
| SonarQube Maven Plugin | 4.0.x | `mvn sonar:sonar` |

---

## Próximos Passos

- [ ] Introduzir _code smells_ intencionais (God Class, Long Method, Feature Envy, etc.)
- [ ] Coletar **baseline** de métricas estáticas no SonarQube
- [ ] Coletar **baseline** de métricas dinâmicas com K6 + Grafana
- [ ] Atualizar `infra/k6/load-test.js` com os endpoints reais do protótipo
- [ ] Aplicar refatorações guiadas pelas métricas
- [ ] Alterar label `fase: pos-refatoracao` em `infra/prometheus.yml`
- [ ] Coletar métricas pós-refatoração
- [ ] Comparar resultados (Fase 1 × Fase 3) para embasar as conclusões do TCC

---

## Troubleshooting

### SonarQube não sobe — `vm.max_map_count`

O Elasticsearch embutido exige `vm.max_map_count ≥ 524288` no kernel do host.

**Linux:**
```bash
# Imediato
sudo sysctl -w vm.max_map_count=524288

# Persistir entre reboots
echo "vm.max_map_count=524288" | sudo tee -a /etc/sysctl.conf
sudo sysctl -p
```

**Windows (Docker Desktop com WSL2):**
```powershell
wsl -d docker-desktop sysctl -w vm.max_map_count=524288
```

Para persistir no Windows, crie `%USERPROFILE%\.wslconfig`:
```ini
[wsl2]
kernelCommandLine = sysctl.vm.max_map_count=524288
```

### Prometheus com status DOWN em `/targets`

1. Confirme que o Spring Boot está rodando: `curl http://localhost:8080/actuator/health`
2. Confirme que o container do Prometheus tem `extra_hosts: host.docker.internal:host-gateway`
3. Verifique os logs: `docker logs tcc-prometheus`

### Limites de recursos

| Container | RAM (limite) | RAM (reserva) |
|---|---|---|
| `tcc-sonarqube` | 3 GB | 2 GB |
| `tcc-postgres` | 512 MB | 128 MB |
| `tcc-prometheus` | 512 MB | 128 MB |
| `tcc-grafana` | 256 MB | 64 MB |
| **Total infra** | **~4.3 GB** | |

> Para máquinas com pouca RAM, suba apenas o necessário:
> ```bash
> # Apenas análise estática
> docker compose -f infra/docker-compose.infra.yml up -d sonarqube postgres
>
> # Apenas observabilidade dinâmica
> docker compose -f infra/docker-compose.infra.yml up -d prometheus grafana
> ```
