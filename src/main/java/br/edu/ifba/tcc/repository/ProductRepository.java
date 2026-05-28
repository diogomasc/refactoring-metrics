package br.edu.ifba.tcc.repository;

import br.edu.ifba.tcc.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
