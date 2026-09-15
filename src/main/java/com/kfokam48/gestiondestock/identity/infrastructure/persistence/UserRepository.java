package com.kfokam48.gestiondestock.identity.infrastructure.persistence;

import com.kfokam48.gestiondestock.identity.domain.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findUserByEmail(String email);

}
