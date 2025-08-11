// Source code is decompiled from a .class file using FernFlower decompiler.
package com.gescom.repository;

import com.gescom.entity.OrderItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemsRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    void deleteByOrderId(Long orderId);
}
