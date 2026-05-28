package br.edu.ifba.tcc.repository;

import br.edu.ifba.tcc.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
