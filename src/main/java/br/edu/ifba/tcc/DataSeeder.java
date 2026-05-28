package br.edu.ifba.tcc;

import br.edu.ifba.tcc.model.Customer;
import br.edu.ifba.tcc.model.Product;
import br.edu.ifba.tcc.repository.CustomerRepository;
import br.edu.ifba.tcc.repository.ProductRepository;
import com.github.javafaker.Faker;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Popula o H2 com dados gerados pelo JavaFaker a cada inicialização.
 * 30 customers e 50 products para dar volume real ao K6.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final CustomerRepository customerRepository;
    private final ProductRepository productRepository;

    public DataSeeder(CustomerRepository customerRepository,
                      ProductRepository productRepository) {
        this.customerRepository = customerRepository;
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        seedCustomers();
        seedProducts();
    }

    private void seedCustomers() {
        Faker faker = new Faker(new Locale("pt-BR"), new Random(42));
        String[] tiers = {"BRONZE", "SILVER", "GOLD"};
        List<Customer> customers = new ArrayList<>();

        for (int i = 0; i < 30; i++) {
            Customer c = new Customer();
            c.setName(faker.name().fullName());
            c.setEmail(faker.internet().emailAddress());
            c.setAddress(faker.address().fullAddress());
            c.setCreditScore(faker.number().numberBetween(300, 850));
            c.setDiscountTier(tiers[faker.number().numberBetween(0, 3)]);
            customers.add(c);
        }

        customerRepository.saveAll(customers);
        System.out.println("[DataSeeder] " + customers.size() + " customers inseridos.");
    }

    private void seedProducts() {
        Faker faker = new Faker(new Locale("pt-BR"), new Random(123));
        String[] categories = {"ELECTRONICS", "FURNITURE", "OFFICE", "PERIPHERALS", "SOFTWARE"};
        List<Product> products = new ArrayList<>();

        for (int i = 0; i < 50; i++) {
            Product p = new Product();
            p.setName(faker.commerce().productName());
            p.setCategory(categories[faker.number().numberBetween(0, categories.length)]);
            p.setPrice(Double.parseDouble(faker.commerce().price(10.0, 5000.0).replace(",", ".")));
            p.setStockQuantity(faker.number().numberBetween(5, 500));
            products.add(p);
        }

        productRepository.saveAll(products);
        System.out.println("[DataSeeder] " + products.size() + " products inseridos.");
    }
}
