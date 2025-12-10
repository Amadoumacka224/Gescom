package com.gescom.backend.repository;

import com.gescom.backend.entity.ActivityLog;
import com.gescom.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findByUser(User user);

    List<ActivityLog> findByUserId(Long userId);

    List<ActivityLog> findByActionType(ActivityLog.ActionType actionType);

    List<ActivityLog> findByEntity(String entity);

    List<ActivityLog> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime start, LocalDateTime end);

    List<ActivityLog> findByActionTypeAndCreatedAtBetween(ActivityLog.ActionType actionType, LocalDateTime start, LocalDateTime end);

    @Query("SELECT al FROM ActivityLog al WHERE al.user.id = :userId ORDER BY al.createdAt DESC")
    List<ActivityLog> findRecentByUser(Long userId);

    @Query("SELECT al FROM ActivityLog al ORDER BY al.createdAt DESC")
    List<ActivityLog> findAllOrderByCreatedAtDesc();

    @Query("SELECT al FROM ActivityLog al WHERE al.user.role = 'CAISSIER' ORDER BY al.createdAt DESC")
    List<ActivityLog> findCaissierActivities();
}
