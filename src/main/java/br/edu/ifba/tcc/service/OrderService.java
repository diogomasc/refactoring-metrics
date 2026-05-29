package br.edu.ifba.tcc.service;

import br.edu.ifba.tcc.model.Customer;
import br.edu.ifba.tcc.model.Order;
import br.edu.ifba.tcc.model.OrderItem;
import br.edu.ifba.tcc.model.Product;
import br.edu.ifba.tcc.repository.CustomerRepository;
import br.edu.ifba.tcc.repository.OrderRepository;
import br.edu.ifba.tcc.repository.ProductRepository;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * God Class intencional — concentra lógica de validação, cálculo de desconto,
 * persistência, notificação e geração de relatório em uma única classe.
 *
 * Smells presentes:
 * - God Class: responsabilidades demais em uma classe
 * - Long Method: processOrder() com 100+ linhas
 * - Feature Envy: acessa diretamente campos de Customer e Product
 * - Dispersed Coupling: instancia repositórios via Spring mas sem interfaces/abstração
 * - Shotgun Surgery: lógica de desconto duplicada aqui e em DiscountCalculator/ReportService/NotificationService
 *
 * @Observed instrumenta processOrder(), getOrderById() e generateOrdersReport().
 * O tempo de execução de processOrder() sob carga expõe o custo do Long Method
 * e das múltiplas responsabilidades (validação + desconto + persistência).
 * Métrica gerada: "order.service.seconds" (Histogram) → p50/p95/p99 no Grafana.
 */
@Observed(
        name = "order.service",
        contextualName = "servico-de-pedidos-god-class"
)
@Service
public class OrderService {

    // Dispersed Coupling: dependências concretas diretas, sem interfaces
    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public OrderService(OrderRepository orderRepository,
                        CustomerRepository customerRepository,
                        ProductRepository productRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    /**
     * Long Method intencional — processamento completo do pedido em um único método.
     * Valida cliente, valida estoque, calcula total, aplica desconto, persiste e "notifica".
     * Tudo sequencial com if/else aninhados.
     *
     * @param customerId   ID do cliente
     * @param productQuantities Map de productId → quantidade
     * @return Order persistido
     */
    public Order processOrder(Long customerId, Map<Long, Integer> productQuantities) {
        // ── Validação do cliente ──────────────────────────────────────────────
        if (customerId == null) {
            throw new RuntimeException("Customer ID não pode ser nulo");
        }

        Optional<Customer> customerOpt = customerRepository.findById(customerId);
        if (customerOpt.isEmpty()) {
            throw new RuntimeException("Cliente não encontrado: " + customerId);
        }

        Customer customer = customerOpt.get();

        // Feature Envy: acessando campos do Customer diretamente para validação
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new RuntimeException("Cliente sem nome válido");
        }

        if (customer.getEmail() == null || customer.getEmail().trim().isEmpty()) {
            throw new RuntimeException("Cliente sem email válido");
        }

        // Feature Envy: verificando credit score que deveria ser responsabilidade do Customer
        if (customer.getCreditScore() == null || customer.getCreditScore() < 300) {
            throw new RuntimeException("Cliente com credit score insuficiente: " + customer.getCreditScore());
        }

        // Feature Envy: verificando endereço
        if (customer.getAddress() == null || customer.getAddress().trim().isEmpty()) {
            throw new RuntimeException("Cliente sem endereço cadastrado");
        }

        // ── Validação dos produtos e estoque ─────────────────────────────────
        if (productQuantities == null || productQuantities.isEmpty()) {
            throw new RuntimeException("Pedido deve conter pelo menos um produto");
        }

        List<OrderItem> items = new ArrayList<>();
        double totalAmount = 0.0;

        for (Map.Entry<Long, Integer> entry : productQuantities.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();

            if (quantity == null || quantity <= 0) {
                throw new RuntimeException("Quantidade inválida para produto " + productId);
            }

            Optional<Product> productOpt = productRepository.findById(productId);
            if (productOpt.isEmpty()) {
                throw new RuntimeException("Produto não encontrado: " + productId);
            }

            Product product = productOpt.get();

            // Feature Envy: acessando stockQuantity do Product diretamente
            if (product.getStockQuantity() == null || product.getStockQuantity() < quantity) {
                throw new RuntimeException("Estoque insuficiente para produto '"
                        + product.getName() + "'. Disponível: "
                        + product.getStockQuantity() + ", Solicitado: " + quantity);
            }

            // Feature Envy: acessando price do Product para cálculos
            double itemTotal = product.getPrice() * quantity;
            totalAmount += itemTotal;

            OrderItem orderItem = new OrderItem();
            orderItem.setProductId(productId);
            orderItem.setQuantity(quantity);
            orderItem.setUnitPrice(product.getPrice());
            items.add(orderItem);

            // Atualiza estoque diretamente — sem abstração
            product.setStockQuantity(product.getStockQuantity() - quantity);
            productRepository.save(product);
        }

        // ── Cálculo de desconto (lógica duplicada — Shotgun Surgery) ─────────
        // Esta mesma lógica está em DiscountCalculator, ReportService e NotificationService
        double discountPercentage = 0.0;

        // Feature Envy: acessa discountTier do Customer
        String discountTier = customer.getDiscountTier();
        if (discountTier != null) {
            if (discountTier.equals("GOLD")) {
                discountPercentage = 0.15;
            } else if (discountTier.equals("SILVER")) {
                discountPercentage = 0.10;
            } else if (discountTier.equals("BRONZE")) {
                discountPercentage = 0.05;
            }
        }

        // Feature Envy: acessa creditScore do Customer para bônus
        int creditScore = customer.getCreditScore();
        if (creditScore > 700) {
            discountPercentage += 0.03;
        } else if (creditScore > 500) {
            discountPercentage += 0.01;
        }

        // Desconto adicional para pedidos grandes
        if (totalAmount > 5000.0) {
            discountPercentage += 0.05;
        } else if (totalAmount > 1000.0) {
            discountPercentage += 0.02;
        }

        double discountApplied = totalAmount * discountPercentage;
        double finalAmount = totalAmount - discountApplied;

        // ── Criação e persistência do pedido ─────────────────────────────────
        Order order = new Order();
        order.setCustomerId(customerId);
        order.setTotalAmount(finalAmount);
        order.setDiscountApplied(discountApplied);
        order.setStatus("CONFIRMED");
        order.setCreatedAt(LocalDateTime.now());

        // Associa os items ao order
        for (OrderItem item : items) {
            item.setOrder(order);
        }
        order.setItems(items);

        Order savedOrder = orderRepository.save(order);

        // ── "Notificação" simulada (God Class: responsabilidade que não deveria estar aqui)
        System.out.println("[OrderService] Pedido #" + savedOrder.getId()
                + " confirmado para cliente '" + customer.getName()
                + "' (" + customer.getEmail() + ")"
                + " | Total: R$ " + String.format("%.2f", finalAmount)
                + " | Desconto: R$ " + String.format("%.2f", discountApplied)
                + " | Tier: " + customer.getDiscountTier()
                + " | Endereço: " + customer.getAddress());

        // ── "Log de auditoria" simulado (mais uma responsabilidade indevida)
        System.out.println("[AUDIT] " + LocalDateTime.now()
                + " | ORDER_CREATED | orderId=" + savedOrder.getId()
                + " | customerId=" + customerId
                + " | items=" + items.size()
                + " | total=" + finalAmount);

        return savedOrder;
    }

    /**
     * Busca pedido por ID — expõe N+1 queries via FetchType.EAGER em Order.items
     */
    public Order getOrderById(Long orderId) {
        if (orderId == null) {
            throw new RuntimeException("Order ID não pode ser nulo");
        }

        Optional<Order> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new RuntimeException("Pedido não encontrado: " + orderId);
        }

        Order order = orderOpt.get();

        // Feature Envy: busca o Customer para enriquecer dados (acoplamento desnecessário)
        Optional<Customer> customerOpt = customerRepository.findById(order.getCustomerId());
        if (customerOpt.isPresent()) {
            Customer customer = customerOpt.get();
            System.out.println("[OrderService] Pedido #" + orderId
                    + " consultado | Cliente: " + customer.getName()
                    + " | Tier: " + customer.getDiscountTier());
        }

        return order;
    }

    /**
     * Gera relatório de pedidos — duplica lógica de desconto (Shotgun Surgery).
     * Este método deveria estar em um ReportService dedicado.
     */
    public Map<String, Object> generateOrdersReport() {
        List<Order> allOrders = orderRepository.findAll();
        Map<String, Object> report = new HashMap<>();

        double totalRevenue = 0.0;
        double totalDiscounts = 0.0;
        int totalItems = 0;
        Map<String, Integer> ordersByTier = new HashMap<>();

        for (Order order : allOrders) {
            totalRevenue += order.getTotalAmount();
            totalDiscounts += (order.getDiscountApplied() != null ? order.getDiscountApplied() : 0.0);
            totalItems += order.getItems().size();

            // Feature Envy: busca Customer para cada pedido (N+1 agravado)
            Optional<Customer> customerOpt = customerRepository.findById(order.getCustomerId());
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();
                String tier = customer.getDiscountTier();
                ordersByTier.merge(tier, 1, Integer::sum);

                // Shotgun Surgery: recalcula desconto com lógica duplicada
                double recalculatedDiscount = 0.0;
                if (tier != null) {
                    if (tier.equals("GOLD")) {
                        recalculatedDiscount = 0.15;
                    } else if (tier.equals("SILVER")) {
                        recalculatedDiscount = 0.10;
                    } else if (tier.equals("BRONZE")) {
                        recalculatedDiscount = 0.05;
                    }
                }

                if (customer.getCreditScore() > 700) {
                    recalculatedDiscount += 0.03;
                } else if (customer.getCreditScore() > 500) {
                    recalculatedDiscount += 0.01;
                }
                // Nota: variável recalculatedDiscount existe para demonstrar a duplicação
            }
        }

        report.put("totalOrders", allOrders.size());
        report.put("totalRevenue", totalRevenue);
        report.put("totalDiscounts", totalDiscounts);
        report.put("totalItems", totalItems);
        report.put("ordersByTier", ordersByTier);
        report.put("generatedAt", LocalDateTime.now().toString());

        System.out.println("[OrderService] Relatório gerado | Pedidos: " + allOrders.size()
                + " | Receita: R$ " + String.format("%.2f", totalRevenue));

        return report;
    }
}
