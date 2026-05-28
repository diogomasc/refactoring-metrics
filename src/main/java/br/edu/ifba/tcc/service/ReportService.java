package br.edu.ifba.tcc.service;

import br.edu.ifba.tcc.model.Customer;
import br.edu.ifba.tcc.model.Order;
import br.edu.ifba.tcc.repository.CustomerRepository;
import br.edu.ifba.tcc.repository.OrderRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Shotgun Surgery intencional — duplica a lógica de desconto que já está
 * em OrderService e DiscountCalculator.
 * Alterar a regra de desconto exige modificar este arquivo também.
 */
@Service
public class ReportService {

    private final OrderRepository orderRepository;
    private final CustomerRepository customerRepository;

    public ReportService(OrderRepository orderRepository,
                         CustomerRepository customerRepository) {
        this.orderRepository = orderRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * Gera relatório detalhado com recálculo de descontos.
     * A lógica de desconto é duplicada do OrderService (Shotgun Surgery).
     */
    public Map<String, Object> generateDetailedReport() {
        List<Order> orders = orderRepository.findAll();
        Map<String, Object> report = new HashMap<>();

        double totalRevenue = 0.0;
        double totalDiscountsRecalculated = 0.0;
        int goldOrders = 0;
        int silverOrders = 0;
        int bronzeOrders = 0;

        for (Order order : orders) {
            totalRevenue += order.getTotalAmount();

            // Feature Envy: busca Customer para recalcular desconto
            Optional<Customer> customerOpt = customerRepository.findById(order.getCustomerId());
            if (customerOpt.isPresent()) {
                Customer customer = customerOpt.get();

                // ── Shotgun Surgery: lógica duplicada de desconto ──────────────
                double discountPercentage = 0.0;
                String tier = customer.getDiscountTier();

                if (tier != null) {
                    if (tier.equals("GOLD")) {
                        discountPercentage = 0.15;
                        goldOrders++;
                    } else if (tier.equals("SILVER")) {
                        discountPercentage = 0.10;
                        silverOrders++;
                    } else if (tier.equals("BRONZE")) {
                        discountPercentage = 0.05;
                        bronzeOrders++;
                    }
                }

                if (customer.getCreditScore() > 700) {
                    discountPercentage += 0.03;
                } else if (customer.getCreditScore() > 500) {
                    discountPercentage += 0.01;
                }

                double orderTotal = order.getTotalAmount() + (order.getDiscountApplied() != null ? order.getDiscountApplied() : 0.0);
                if (orderTotal > 5000.0) {
                    discountPercentage += 0.05;
                } else if (orderTotal > 1000.0) {
                    discountPercentage += 0.02;
                }

                totalDiscountsRecalculated += orderTotal * discountPercentage;
            }
        }

        report.put("totalOrders", orders.size());
        report.put("totalRevenue", totalRevenue);
        report.put("totalDiscountsRecalculated", totalDiscountsRecalculated);
        report.put("goldOrders", goldOrders);
        report.put("silverOrders", silverOrders);
        report.put("bronzeOrders", bronzeOrders);
        report.put("generatedAt", LocalDateTime.now().toString());

        System.out.println("[ReportService] Relatório detalhado gerado | "
                + orders.size() + " pedidos | Receita: R$ " + String.format("%.2f", totalRevenue));

        return report;
    }
}
