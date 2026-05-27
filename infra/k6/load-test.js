// --- Script de carga do TCC ---
// Mede latência, throughput e taxa de erro antes e após a refatoração.
//
// Execute a partir da raiz do projeto:
//   docker compose -f infra/docker-compose.infra.yml \
//     --profile testing run --rm k6 run /scripts/load-test.js
//
// Métricas observadas no Grafana:
//   - latência p50/p95/p99 por endpoint
//   - throughput em requisições por segundo
//   - taxa de erro
//   - comportamento da pilha via Observation API do Spring
//
// ⚠️  Use EXATAMENTE a mesma carga nas fases baseline e pós-refatoração
//     para garantir comparabilidade dos resultados.

import http from "k6/http";
import { check, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";

// --- Métricas personalizadas ---
const latenciaPorEndpoint = new Trend("latencia_por_endpoint", true);
const taxaErro = new Rate("taxa_erro");

// --- Perfil de carga ---
// Não altere os stages entre as fases baseline e pós-refatoração.
export const options = {
  stages: [
    { duration: "30s", target: 10 },  // ramp-up gradual
    { duration: "2m", target: 10 },  // carga sustentada (comportamento normal)
    { duration: "30s", target: 50 },  // spike de tráfego
    { duration: "1m", target: 50 },  // comportamento sob estresse
    { duration: "30s", target: 0 },  // ramp-down
  ],
  thresholds: {
    http_req_duration: ["p(95)<2000"], // 95% das requisições < 2s
    taxa_erro: ["rate<0.05"],  // taxa de erro < 5%
  },
};

// --- Base URL ---
// O K6 roda dentro do Docker e acessa o Spring Boot rodando no host.
const BASE_URL = "http://host.docker.internal:8080";

// --- Endpoints do protótipo ---
// Atualize esta lista sempre que novos endpoints forem adicionados ao protótipo.
// Preserve os endpoints existentes entre as fases para manter comparabilidade.
const ENDPOINTS = [
  // ── GET simples ──────────────────────────────────────────────────────────
  // Adicione aqui os endpoints GET do seu protótipo.
  // Exemplo:
  // { nome: "listar-pedidos", path: "/pedidos", metodo: "GET" },

  // ── POST com body ─────────────────────────────────────────────────────────
  // Adicione aqui os endpoints POST do seu protótipo.
  // Exemplo:
  // {
  //   nome: "criar-pedido",
  //   path: "/pedidos",
  //   metodo: "POST",
  //   body: JSON.stringify({ descricao: "Pedido K6", valor: 150.00 }),
  //   headers: { "Content-Type": "application/json" },
  // },

  // ── Actuator (baseline de observabilidade) ────────────────────────────────
  // Mantém uma requisição ao actuator para validar que a app está respondendo.
  { nome: "actuator-health", path: "/actuator/health", metodo: "GET" },
];

// --- Execução principal ---
export default function executarCarga() {
  ENDPOINTS.forEach(({ nome, path, metodo, body, headers }) => {
    const url = `${BASE_URL}${path}`;
    const params = { headers: headers || {} };

    const res =
      metodo === "POST"
        ? http.post(url, body, params)
        : http.get(url, params);

    // Registra latência por endpoint → visível no Grafana como `latencia_por_endpoint{endpoint="..."}`.
    latenciaPorEndpoint.add(res.timings.duration, { endpoint: nome });

    const ok = check(res, {
      [`${nome}: status 2xx`]: (r) => r.status >= 200 && r.status < 300,
    });

    // Taxa de erro consolidada para os thresholds do teste.
    taxaErro.add(!ok);
  });

  sleep(1);
}
