package com.gescom.backend.repository;

import com.gescom.backend.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
    Optional<Category> findByCode(String code);
    List<Category> findByActiveTrue();
    List<Category> findByActiveTrueOrderByNameAsc();
}
