package br.edu.ifba.tcc.controller;

import br.edu.ifba.tcc.model.Order;
import br.edu.ifba.tcc.service.NotificationService;
import br.edu.ifba.tcc.service.OrderService;
import br.edu.ifba.tcc.service.ReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller — expõe os três endpoints que o K6 vai bater:
 * POST /orders         → processOrder() (God Class, Long Method)
 * GET  /orders/{id}    → consulta com N+1 query implícita
 * GET  /orders/report  → relatório que duplica lógica de desconto (Shotgun Surgery)
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final ReportService reportService;
    private final NotificationService notificationService;

    public OrderController(OrderService orderService,
                           ReportService reportService,
                           NotificationService notificationService) {
        this.orderService = orderService;
        this.reportService = reportService;
        this.notificationService = notificationService;
    }

    /**
     * POST /orders
     * Body esperado:
     * {
     *   "customerId": 1,
     *   "items": {
     *     "1": 2,
     *     "3": 1
     *   }
     * }
     * Onde items é um Map de productId → quantidade.
     */
    @PostMapping
    public ResponseEntity<?> createOrder(@RequestBody OrderRequest request) {
        try {
            Order order = orderService.processOrder(request.getCustomerId(), request.getItems());

            // Dispara notificação (acoplamento direto ao NotificationService)
            notificationService.notifyOrderConfirmation(order);

            return ResponseEntity.status(HttpStatus.CREATED).body(order);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * GET /orders/{id}
     * Consulta com N+1 query implícita (FetchType.EAGER em Order.items).
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(@PathVariable Long id) {
        try {
            Order order = orderService.getOrderById(id);
            return ResponseEntity.ok(order);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
        }
    }

    /**
     * GET /orders/report
     * Relatório que duplica lógica de desconto (Shotgun Surgery via ReportService).
     */
    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getReport() {
        Map<String, Object> report = reportService.generateDetailedReport();
        return ResponseEntity.ok(report);
    }

    /**
     * DTO para a requisição POST /orders.
     * Classe interna simples — sem validação (anêmica de propósito).
     */
    public static class OrderRequest {
        private Long customerId;
        private Map<Long, Integer> items;

        public OrderRequest() {
        }

        public Long getCustomerId() {
            return customerId;
        }

        public void setCustomerId(Long customerId) {
            this.customerId = customerId;
        }

        public Map<Long, Integer> getItems() {
            return items;
        }

        public void setItems(Map<Long, Integer> items) {
            this.items = items;
        }
    }
}
