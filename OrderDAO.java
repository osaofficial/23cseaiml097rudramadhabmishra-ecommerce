package com.shop.dao;

import com.shop.utils.DBUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class OrderDAO {

    /**
     * Places an order transactionally.
     * @param userId buyer id
     * @param cart map of productId -> quantity
     * @param total total amount
     * @return created order id or -1 on failure
     */
    public int placeOrder(int userId, Map<Integer, Integer> cart, BigDecimal total) {
        String insertOrderSql = "INSERT INTO orders (user_id, total_amount, status) VALUES (?, ?, ?)";
        String insertItemSql = "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (?, ?, ?, ?)";
        String reduceStockSql = "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?";

        Connection con = null;
        try {
            con = DBUtil.getConnection();
            con.setAutoCommit(false);

            // Insert order
            try (PreparedStatement ps = con.prepareStatement(insertOrderSql, PreparedStatement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, userId);
                ps.setBigDecimal(2, total);
                ps.setString(3, "Pending");
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int orderId = rs.getInt(1);

                        // Insert items and reduce stock
                        try (PreparedStatement psItem = con.prepareStatement(insertItemSql);
                             PreparedStatement psReduce = con.prepareStatement(reduceStockSql)) {
                            com.shop.dao.ProductDAO pdao = new ProductDAO();
                            for (Map.Entry<Integer, Integer> e : cart.entrySet()) {
                                int pid = e.getKey();
                                int qty = e.getValue();
                                // get current price
                                com.shop.models.Product prod = pdao.getProductById(pid);
                                if (prod == null) {
                                    throw new SQLException("Product not found: " + pid);
                                }
                                BigDecimal price = prod.getPrice();

                                psItem.setInt(1, orderId);
                                psItem.setInt(2, pid);
                                psItem.setInt(3, qty);
                                psItem.setBigDecimal(4, price);
                                psItem.executeUpdate();

                                // reduce stock
                                psReduce.setInt(1, qty);
                                psReduce.setInt(2, pid);
                                psReduce.setInt(3, qty);
                                int updated = psReduce.executeUpdate();
                                if (updated == 0) {
                                    throw new SQLException("Insufficient stock for product " + pid);
                                }
                            }
                        }

                        con.commit();
                        return orderId;
                    } else {
                        throw new SQLException("Failed to get order id.");
                    }
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            if (con != null) try { con.rollback(); } catch (SQLException ignore) {}
            return -1;
        } finally {
            if (con != null) try { con.setAutoCommit(true); con.close(); } catch (SQLException ignore) {}
        }
    }
}
