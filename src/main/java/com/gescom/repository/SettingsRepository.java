package com.gescom.repository;

import com.gescom.entity.Settings;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository pour la gestion des paramètres système
 * Avec requêtes optimisées et pagination
 */
@Repository
public interface SettingsRepository extends JpaRepository<Settings, Long> {

    /**
     * Trouve un paramètre par sa clé
     */
    Optional<Settings> findByKey(String key);

    /**
     * Trouve tous les paramètres d'une catégorie, triés par ordre et clé
     */
    List<Settings> findByCategoryOrderBySortOrderAscKeyAsc(Settings.SettingCategory category);

    /**
     * Trouve tous les paramètres non-système
     */
    List<Settings> findByIsSystemFalseOrderByCategoryAscSortOrderAscKeyAsc();

    /**
     * Trouve tous les paramètres système
     */
    List<Settings> findByIsSystemTrueOrderByCategoryAscSortOrderAscKeyAsc();

    /**
     * Recherche avancée avec pagination
     */
    @Query("SELECT s FROM Settings s WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(s.key) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:category IS NULL OR s.category = :category) AND " +
           "(:isSystem IS NULL OR s.isSystem = :isSystem)")
    Page<Settings> findWithFilters(@Param("keyword") String keyword,
                                 @Param("category") Settings.SettingCategory category,
                                 @Param("isSystem") Boolean isSystem,
                                 Pageable pageable);

    /**
     * Recherche simple par mot-clé
     */
    @Query("SELECT s FROM Settings s WHERE " +
           "LOWER(s.key) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(s.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "ORDER BY s.category ASC, s.sortOrder ASC, s.key ASC")
    List<Settings> searchByKeyword(@Param("keyword") String keyword);

    /**
     * Vérifie si une clé existe
     */
    boolean existsByKey(String key);

    /**
     * Compte les paramètres par catégorie
     */
    @Query("SELECT s.category as category, COUNT(s) as count FROM Settings s GROUP BY s.category ORDER BY s.category")
    List<CategoryCount> countByCategory();

    /**
     * Interface de projection pour le comptage par catégorie
     */
    interface CategoryCount {
        Settings.SettingCategory getCategory();
        Long getCount();
    }

    /**
     * Trouve tous les paramètres triés par catégorie et ordre
     */
    List<Settings> findAllByOrderByCategoryAscSortOrderAscKeyAsc();

    /**
     * Trouve les paramètres modifiés récemment
     */
    @Query("SELECT s FROM Settings s WHERE s.updatedAt >= :since ORDER BY s.updatedAt DESC")
    List<Settings> findRecentlyModified(@Param("since") LocalDateTime since);

    /**
     * Trouve le prochain ordre de tri pour une catégorie
     */
    @Query("SELECT COALESCE(MAX(s.sortOrder), 0) + 1 FROM Settings s WHERE s.category = :category")
    Integer findNextSortOrderForCategory(@Param("category") Settings.SettingCategory category);

    /**
     * Trouve les paramètres par type de valeur
     */
    List<Settings> findByValueType(Settings.ValueType valueType);

    /**
     * Trouve les paramètres cryptés
     */
    List<Settings> findByIsEncryptedTrue();

    /**
     * Supprime tous les paramètres d'une catégorie (sauf système)
     */
    void deleteByCategoryAndIsSystemFalse(Settings.SettingCategory category);

    /**
     * Compte le nombre total de paramètres
     */
    @Query("SELECT COUNT(s) FROM Settings s")
    long countTotal();

    /**
     * Compte les paramètres système
     */
    @Query("SELECT COUNT(s) FROM Settings s WHERE s.isSystem = true")
    long countSystem();

    /**
     * Compte les paramètres utilisateur
     */
    @Query("SELECT COUNT(s) FROM Settings s WHERE s.isSystem = false")
    long countUser();

    /**
     * Compte les paramètres modifiés récemment
     */
    @Query("SELECT COUNT(s) FROM Settings s WHERE s.updatedAt >= :since")
    long countModifiedSince(@Param("since") LocalDateTime since);

    /**
     * Trouve les paramètres avec des valeurs invalides (compatible PostgreSQL)
     */
    @Query("SELECT s FROM Settings s WHERE " +
           "(s.valueType = 'INTEGER' AND s.value IS NOT NULL AND s.value NOT LIKE '%[^0-9-]%') OR " +
           "(s.valueType = 'BOOLEAN' AND s.value IS NOT NULL AND s.value NOT IN ('true', 'false'))")
    List<Settings> findInvalidSettings();

    /**
     * Recherche full-text optimisée (compatible PostgreSQL)
     */
    @Query("SELECT s FROM Settings s WHERE " +
           "LOWER(s.key) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(s.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "ORDER BY s.category ASC, s.sortOrder ASC")
    List<Settings> fullTextSearch(@Param("searchTerm") String searchTerm);
}