package br.edu.ifba.tcc.util;

/**
 * Lógica de desconto duplicada intencionalmente.
 * A mesma lógica está em OrderService, ReportService e NotificationService.
 * Smell: Shotgun Surgery — alterar a regra de desconto exige modificar 3+ arquivos.
 */
public class DiscountCalculator {

    public static double calculateDiscount(String discountTier, int creditScore, double totalAmount) {
        double discountPercentage = 0.0;

        if (discountTier != null) {
            if (discountTier.equals("GOLD")) {
                discountPercentage = 0.15;
            } else if (discountTier.equals("SILVER")) {
                discountPercentage = 0.10;
            } else if (discountTier.equals("BRONZE")) {
                discountPercentage = 0.05;
            }
        }

        // Bônus extra por credit score alto
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

        return totalAmount * discountPercentage;
    }
}
