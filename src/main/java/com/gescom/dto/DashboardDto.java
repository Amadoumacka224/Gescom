package com.gescom.dto;

import java.math.BigDecimal;
import java.util.List;

public class DashboardDto {

    private int periodDays = 30;
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    private Long totalOrders = 0L;
    private Long totalClients = 0L;
    private Long totalUsers = 0L;
    private Double revenueGrowth;
    private Long pendingOrders = 0L;
    private List<TopSeller> topSellers;
    private List<LowStockProduct> lowStockProducts;
    private List<RevenueByDay> revenueByDay;
    private List<OrdersByStatus> ordersByStatus;

    // Constructeurs
    public DashboardDto() {}

    public DashboardDto(int periodDays) {
        this.periodDays = periodDays;
    }

    // Classes internes pour les données des graphiques
    public static class TopSeller {
        private String name;
        private BigDecimal revenue;
        private Long orders;

        public TopSeller(String name, BigDecimal revenue, Long orders) {
            this.name = name;
            this.revenue = revenue;
            this.orders = orders;
        }

        // Getters et setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
        public Long getOrders() { return orders; }
        public void setOrders(Long orders) { this.orders = orders; }
    }

    public static class LowStockProduct {
        private String name;
        private Integer stock;

        public LowStockProduct(String name, Integer stock) {
            this.name = name;
            this.stock = stock;
        }

        // Getters et setters
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
    }

    public static class RevenueByDay {
        private String date;
        private BigDecimal revenue;

        public RevenueByDay(String date, BigDecimal revenue) {
            this.date = date;
            this.revenue = revenue;
        }

        // Getters et setters
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public BigDecimal getRevenue() { return revenue; }
        public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }
    }

    public static class OrdersByStatus {
        private String status;
        private Long count;

        public OrdersByStatus(String status, Long count) {
            this.status = status;
            this.count = count;
        }

        // Getters et setters
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getCount() { return count; }
        public void setCount(Long count) { this.count = count; }
    }

    // Getters et setters principaux
    public int getPeriodDays() { return periodDays; }
    public void setPeriodDays(int periodDays) { this.periodDays = periodDays; }

    public BigDecimal getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(BigDecimal totalRevenue) { this.totalRevenue = totalRevenue; }

    public Long getTotalOrders() { return totalOrders; }
    public void setTotalOrders(Long totalOrders) { this.totalOrders = totalOrders; }

    public Long getTotalClients() { return totalClients; }
    public void setTotalClients(Long totalClients) { this.totalClients = totalClients; }

    public Long getTotalUsers() { return totalUsers; }
    public void setTotalUsers(Long totalUsers) { this.totalUsers = totalUsers; }

    public Double getRevenueGrowth() { return revenueGrowth; }
    public void setRevenueGrowth(Double revenueGrowth) { this.revenueGrowth = revenueGrowth; }

    public Long getPendingOrders() { return pendingOrders; }
    public void setPendingOrders(Long pendingOrders) { this.pendingOrders = pendingOrders; }

    public List<TopSeller> getTopSellers() { return topSellers; }
    public void setTopSellers(List<TopSeller> topSellers) { this.topSellers = topSellers; }

    public List<LowStockProduct> getLowStockProducts() { return lowStockProducts; }
    public void setLowStockProducts(List<LowStockProduct> lowStockProducts) { this.lowStockProducts = lowStockProducts; }

    public List<RevenueByDay> getRevenueByDay() { return revenueByDay; }
    public void setRevenueByDay(List<RevenueByDay> revenueByDay) { this.revenueByDay = revenueByDay; }

    public List<OrdersByStatus> getOrdersByStatus() { return ordersByStatus; }
    public void setOrdersByStatus(List<OrdersByStatus> ordersByStatus) { this.ordersByStatus = ordersByStatus; }
}