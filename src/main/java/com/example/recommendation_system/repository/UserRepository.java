package com.example.recommendation_system.repository;

import com.example.recommendation_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {
}
