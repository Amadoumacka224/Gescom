package com.gescom.repository;




import com.gescom.entity.Client;
import com.gescom.entity.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface ClientRepository extends JpaRepository<Client, Long> {
    Long countByAssignedUserIn(List<User> users);

    Long countByAssignedUserInAndCreatedAtAfter(List<User> users, LocalDateTime startDate);

    Long countByAssignedUser(User user);

    @Query("SELECT COUNT(DISTINCT c) FROM Client c JOIN Order o ON o.client = c WHERE c.assignedUser = :user AND o.orderDate >= :startDate")
    Long countActiveClientsByUser(@Param("user") User user, @Param("startDate") LocalDateTime startDate);

    @Query("SELECT c FROM Client c WHERE c.assignedUser = :user AND c.followUpDate <= CURRENT_TIMESTAMP AND c.status = 'ACTIVE'")
    List<Client> findPendingFollowUpsByUser(@Param("user") User user);

    @Query("SELECT new map(DATE(c.createdAt) as date, COUNT(c) as count) FROM Client c WHERE c.createdAt >= :startDate GROUP BY DATE(c.createdAt) ORDER BY DATE(c.createdAt)")
    List<Map<String, Object>> getNewClientsByDay(@Param("startDate") LocalDateTime startDate);

    Optional<Client> findByEmail(String email);

    List<Client> findByStatus(Client.ClientStatus status);

    List<Client> findByClientType(Client.ClientType clientType);

    List<Client> findByAssignedUser(User assignedUser);

    List<Client> findByCity(String city);

    List<Client> findTop10ByOrderByCreatedAtDesc();


    @Query("SELECT c FROM Client c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Client> searchByNameOrEmail(@Param("search") String search);

    @Query("SELECT new map(CONCAT(u.firstName, ' ', u.lastName) as commercial, COUNT(c) as totalClients, SUM(CASE WHEN c.status = 'ACTIVE' THEN 1 ELSE 0 END) as activeClients, SUM(CASE WHEN c.createdAt >= :startDate THEN 1 ELSE 0 END) as newClients) FROM Client c JOIN c.assignedUser u GROUP BY u.id, u.firstName, u.lastName ORDER BY COUNT(c) DESC")
    List<Map<String, Object>> getClientStatsByUser(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT new map(c.clientType as type, COUNT(c) as count) FROM Client c GROUP BY c.clientType")
    List<Map<String, Object>> getClientDistributionByType();

    @Query("SELECT new map(c.status as status, COUNT(c) as count) FROM Client c GROUP BY c.status")
    List<Map<String, Object>> getClientDistributionByStatus();

    @Query("SELECT new map(c.name as name, c.email as email, COALESCE(SUM(o.totalAmount), 0) as revenue, COUNT(o) as orders) FROM Client c LEFT JOIN c.orders o WITH o.orderDate >= :startDate AND o.status != 'CANCELLED' GROUP BY c.id, c.name, c.email ORDER BY SUM(o.totalAmount) DESC")
    List<Map<String, Object>> getTopClientsByRevenue(@Param("startDate") LocalDateTime startDate);

    @Query("SELECT c FROM Client c WHERE c.status = 'ACTIVE' AND (SELECT MAX(o.orderDate) FROM Order o WHERE o.client = c) < :cutoffDate")
    List<Client> findInactiveClients(@Param("cutoffDate") LocalDateTime cutoffDate);
}
