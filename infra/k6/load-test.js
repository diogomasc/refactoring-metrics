// --- Script de carga do TCC ---
// Mede latência, throughput e taxa de erro antes e após a refatoração.
//
// Endpoints cobertos:
//   POST /orders              → processOrder (God Class + Long Method)
//   GET  /orders/{id}         → getOrder     (N+1 query implícita)
//   GET  /orders/report       → generateDetailedReport (Shotgun Surgery)
//   GET  /actuator/health     → baseline de observabilidade
//
// Execute a partir da raiz do projeto:
//   docker compose -f infra/docker-compose.infra.yml \
//     --profile testing run --rm k6 run /scripts/load-test.js
//
// ⚠️  Não altere stages, thresholds nem a carga entre as fases
//     baseline e pós-refatoração para garantir comparabilidade dos resultados.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { SharedArray } from "k6/data";
import { Trend, Rate, Counter } from "k6/metrics";

// ── Métricas personalizadas (RED methodology) ─────────────────────────────────
// Trend → latência por endpoint (p50 / p95 / p99 visíveis no Grafana)
const latenciaCriarPedido   = new Trend("latencia_criar_pedido",   true);
const latenciaConsultarPedido = new Trend("latencia_consultar_pedido", true);
const latenciaRelatorio     = new Trend("latencia_relatorio",      true);
const latenciaHealth        = new Trend("latencia_health",         true);

// Rate  → taxa de erro consolidada (threshold global)
const taxaErroGlobal = new Rate("taxa_erro");

// Counter → total de pedidos criados com sucesso (rastreabilidade)
const pedidosCriados = new Counter("pedidos_criados_com_sucesso");

// ── Base URL ──────────────────────────────────────────────────────────────────
// O K6 roda dentro do Docker; host.docker.internal resolve o Spring Boot no host.
// Altere a porta se o protótipo mudar de 8080.
const BASE_URL = "http://host.docker.internal:8080";

// ── Dados parametrizados (SharedArray — carregado uma vez, compartilhado entre VUs) ──
// Garante que cada VU use um customerId e um conjunto de items diferente,
// tornando o teste mais realista e cobrindo múltiplos clientes do seed.
const pedidosPayload = new SharedArray("pedidos", function () {
  // DataSeeder cria 50 customers (IDs 1-50) e 20 products (IDs 1-20).
  // Cada entrada representa um pedido plausível com 1-3 itens.
  return [
    { customerId: 1,  items: { "1": 1, "2": 1 } },
    { customerId: 2,  items: { "3": 2 } },
    { customerId: 3,  items: { "4": 1, "5": 1, "6": 1 } },
    { customerId: 4,  items: { "7": 2 } },
    { customerId: 5,  items: { "8": 1 } },
    { customerId: 6,  items: { "9": 1, "10": 1 } },
    { customerId: 7,  items: { "11": 3 } },
    { customerId: 8,  items: { "12": 1 } },
    { customerId: 9,  items: { "13": 1, "14": 1 } },
    { customerId: 10, items: { "15": 2 } },
    { customerId: 11, items: { "16": 1 } },
    { customerId: 12, items: { "17": 1, "18": 1 } },
    { customerId: 13, items: { "19": 2 } },
    { customerId: 14, items: { "20": 1 } },
    { customerId: 15, items: { "1": 1, "5": 1 } },
    { customerId: 16, items: { "2": 2, "8": 1 } },
    { customerId: 17, items: { "3": 1 } },
    { customerId: 18, items: { "10": 1, "12": 1 } },
    { customerId: 19, items: { "14": 2 } },
    { customerId: 20, items: { "16": 1, "20": 1 } },
  ];
});

// ── Perfil de carga ───────────────────────────────────────────────────────────
// Não altere os stages entre as fases baseline e pós-refatoração.
export const options = {
  stages: [
    { duration: "30s", target: 10 },  // ramp-up gradual
    { duration: "2m",  target: 10 },  // carga sustentada (comportamento normal)
    { duration: "30s", target: 50 },  // spike de tráfego
    { duration: "1m",  target: 50 },  // comportamento sob estresse
    { duration: "30s", target: 0  },  // ramp-down
  ],
  thresholds: {
    // SLOs que devem ser atendidos nas duas fases para comparação justa
    http_req_duration:        ["p(95)<2000"],  // 95% das req < 2 s
    taxa_erro:                ["rate<0.05"],   // taxa de erro < 5%
    latencia_criar_pedido:    ["p(95)<2000"],
    latencia_consultar_pedido:["p(95)<1000"],
    latencia_relatorio:       ["p(95)<3000"],
  },
};

// ── Execução principal ────────────────────────────────────────────────────────
export default function executarCarga() {
  // Seleciona payload da iteração atual (round-robin por VU)
  const payload = pedidosPayload[__VU % pedidosPayload.length];

  // ── Grupo 1: Criar pedido (POST /orders) ─────────────────────────────────
  group("criar-pedido", function () {
    const body   = JSON.stringify(payload);
    const params = { headers: { "Content-Type": "application/json" } };

    const res = http.post(`${BASE_URL}/orders`, body, params);
    latenciaCriarPedido.add(res.timings.duration, { endpoint: "POST /orders" });

    const ok = check(res, {
      "criar-pedido: status 201": (r) => r.status === 201,
      "criar-pedido: body tem id": (r) => {
        try { return JSON.parse(r.body).id !== undefined; }
        catch { return false; }
      },
    });

    taxaErroGlobal.add(!ok);

    // Persiste o orderId para uso no grupo de consulta
    if (ok) {
      pedidosCriados.add(1);
      // Exporta o ID via VU-scoped variable (acessível no mesmo VU)
      __ENV._lastOrderId = String(JSON.parse(res.body).id);
    }
  });

  sleep(0.5);

  // ── Grupo 2: Consultar pedido (GET /orders/{id}) ──────────────────────────
  group("consultar-pedido", function () {
    // Usa o ID criado nesta iteração ou um ID fixo como fallback
    const orderId = __ENV._lastOrderId || "1";
    const res = http.get(`${BASE_URL}/orders/${orderId}`);

    latenciaConsultarPedido.add(res.timings.duration, { endpoint: "GET /orders/{id}" });

    const ok = check(res, {
      "consultar-pedido: status 200": (r) => r.status === 200,
      "consultar-pedido: body tem status": (r) => {
        try { return JSON.parse(r.body).status !== undefined; }
        catch { return false; }
      },
    });

    taxaErroGlobal.add(!ok);
  });

  sleep(0.5);

  // ── Grupo 3: Relatório (GET /orders/report) ───────────────────────────────
  // Só 20% das VUs executam o relatório por iteração (operação pesada — N+1)
  if (__VU % 5 === 0) {
    group("relatorio", function () {
      const res = http.get(`${BASE_URL}/orders/report`);

      latenciaRelatorio.add(res.timings.duration, { endpoint: "GET /orders/report" });

      const ok = check(res, {
        "relatorio: status 200":             (r) => r.status === 200,
        "relatorio: body tem totalOrders":   (r) => {
          try { return JSON.parse(r.body).totalOrders !== undefined; }
          catch { return false; }
        },
      });

      taxaErroGlobal.add(!ok);
    });
  }

  sleep(0.5);

  // ── Grupo 4: Health check (GET /actuator/health) ──────────────────────────
  group("health", function () {
    const res = http.get(`${BASE_URL}/actuator/health`);

    latenciaHealth.add(res.timings.duration, { endpoint: "GET /actuator/health" });

    const ok = check(res, {
      "health: status 200":  (r) => r.status === 200,
      "health: status UP":   (r) => {
        try { return JSON.parse(r.body).status === "UP"; }
        catch { return false; }
      },
    });

    taxaErroGlobal.add(!ok);
  });

  sleep(1);
}
