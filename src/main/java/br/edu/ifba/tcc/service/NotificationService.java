package br.edu.ifba.tcc.service;

import br.edu.ifba.tcc.model.Customer;
import br.edu.ifba.tcc.model.Order;
import br.edu.ifba.tcc.repository.CustomerRepository;
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Shotgun Surgery intencional — duplica a lógica de desconto que já está
 * em OrderService, DiscountCalculator e ReportService.
 * Alterar a regra de desconto exige modificar este arquivo também.
 *
 * @Observed instrumenta notifyOrderConfirmation().
 * Como é chamada sincronamente dentro de OrderController.createOrder(),
 * seu tempo de execução soma à latência total do POST /orders —
 * evidenciando o custo de responsabilidades espalhadas (God Class + Feature Envy).
 * Métrica gerada: "notification.service.seconds" (Histogram).
 */
@Observed(
        name = "notification.service",
        contextualName = "servico-de-notificacao-feature-envy"
)
@Service
public class NotificationService {

    private final CustomerRepository customerRepository;

    public NotificationService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    /**
     * Simula envio de notificação ao cliente após criação do pedido.
     * A lógica de desconto é duplicada para calcular o "desconto informado"
     * na notificação (Shotgun Surgery).
     */
    public void notifyOrderConfirmation(Order order) {
        if (order == null || order.getCustomerId() == null) {
            System.out.println("[NotificationService] Pedido inválido para notificação");
            return;
        }

        Optional<Customer> customerOpt = customerRepository.findById(order.getCustomerId());
        if (customerOpt.isEmpty()) {
            System.out.println("[NotificationService] Cliente não encontrado: " + order.getCustomerId());
            return;
        }

        Customer customer = customerOpt.get();

        // ── Shotgun Surgery: lógica duplicada de desconto ──────────────
        // Mesma lógica de OrderService, ReportService e DiscountCalculator
        double discountPercentage = 0.0;
        String tier = customer.getDiscountTier();

        if (tier != null) {
            if (tier.equals("GOLD")) {
                discountPercentage = 0.15;
            } else if (tier.equals("SILVER")) {
                discountPercentage = 0.10;
            } else if (tier.equals("BRONZE")) {
                discountPercentage = 0.05;
            }
        }

        // Feature Envy: acessa creditScore do Customer
        if (customer.getCreditScore() > 700) {
            discountPercentage += 0.03;
        } else if (customer.getCreditScore() > 500) {
            discountPercentage += 0.01;
        }

        double orderGrossTotal = order.getTotalAmount()
                + (order.getDiscountApplied() != null ? order.getDiscountApplied() : 0.0);
        if (orderGrossTotal > 5000.0) {
            discountPercentage += 0.05;
        } else if (orderGrossTotal > 1000.0) {
            discountPercentage += 0.02;
        }

        // Simula "envio" de email
        System.out.println("[NotificationService] ════════════════════════════════════");
        System.out.println("[NotificationService] NOTIFICAÇÃO DE PEDIDO CONFIRMADO");
        System.out.println("[NotificationService] Para: " + customer.getEmail());
        System.out.println("[NotificationService] Cliente: " + customer.getName());
        System.out.println("[NotificationService] Pedido: #" + order.getId());
        System.out.println("[NotificationService] Total: R$ " + String.format("%.2f", order.getTotalAmount()));
        System.out.println("[NotificationService] Desconto aplicado: R$ "
                + String.format("%.2f", order.getDiscountApplied()));
        System.out.println("[NotificationService] Seu tier: " + tier
                + " (" + String.format("%.0f%%", discountPercentage * 100) + " de desconto)");
        System.out.println("[NotificationService] Endereço de entrega: " + customer.getAddress());
        System.out.println("[NotificationService] Data: " + LocalDateTime.now());
        System.out.println("[NotificationService] ════════════════════════════════════");
    }
}
