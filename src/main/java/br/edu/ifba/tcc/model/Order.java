package br.edu.ifba.tcc.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Pedido — customerId é um campo BIGINT simples, sem @ManyToOne.
 * O OrderService faz lookup manual do Customer, reforçando Feature Envy
 * e Dispersed Coupling.
 *
 * FetchType.EAGER intencional em items: causa N+1 queries mensuráveis
 * no Grafana sob carga K6.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Acoplamento direto por ID — sem @ManyToOne para forçar busca manual
    // no OrderService (evidencia Feature Envy e Dispersed Coupling)
    private Long customerId;

    private Double totalAmount;
    private Double discountApplied;
    private String status; // "PENDING", "CONFIRMED", "CANCELLED"
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(Long id, Long customerId, Double totalAmount, Double discountApplied,
                 String status, LocalDateTime createdAt, List<OrderItem> items) {
        this.id = id;
        this.customerId = customerId;
        this.totalAmount = totalAmount;
        this.discountApplied = discountApplied;
        this.status = status;
        this.createdAt = createdAt;
        this.items = items != null ? items : new ArrayList<>();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(Double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Double getDiscountApplied() {
        return discountApplied;
    }

    public void setDiscountApplied(Double discountApplied) {
        this.discountApplied = discountApplied;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    @Override
    public String toString() {
        return "Order{id=" + id + ", customerId=" + customerId + ", total=" + totalAmount
                + ", status='" + status + "', items=" + (items != null ? items.size() : 0) + "}";
    }
}
