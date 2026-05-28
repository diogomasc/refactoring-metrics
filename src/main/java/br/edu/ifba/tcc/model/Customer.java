package br.edu.ifba.tcc.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Data Class intencional — só getters/setters, zero comportamento.
 * Toda lógica de desconto e validação está "fora" dela, no OrderService.
 * Smell: Data Class (Fowler, 2018).
 */
@Entity
@Table(name = "customers")
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String address;
    private Integer creditScore;
    private String discountTier; // "BRONZE", "SILVER", "GOLD"

    public Customer() {
    }

    public Customer(Long id, String name, String email, String address,
                    Integer creditScore, String discountTier) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.address = address;
        this.creditScore = creditScore;
        this.discountTier = discountTier;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Integer getCreditScore() {
        return creditScore;
    }

    public void setCreditScore(Integer creditScore) {
        this.creditScore = creditScore;
    }

    public String getDiscountTier() {
        return discountTier;
    }

    public void setDiscountTier(String discountTier) {
        this.discountTier = discountTier;
    }

    @Override
    public String toString() {
        return "Customer{id=" + id + ", name='" + name + "', email='" + email
                + "', discountTier='" + discountTier + "'}";
    }
}
