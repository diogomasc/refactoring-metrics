package br.edu.ifba.tcc.repository;

import br.edu.ifba.tcc.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
