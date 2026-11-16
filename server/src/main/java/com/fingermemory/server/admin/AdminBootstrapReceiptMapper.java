package com.fingermemory.server.admin;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminBootstrapReceiptMapper {
    @Select("""
            SELECT receipt_key, user_id, username_normalized, created_at_ms
            FROM fm_admin_bootstrap_receipt
            WHERE receipt_key = #{receiptKey}
            FOR UPDATE
            """)
    AdminBootstrapReceipt findByKeyForUpdate(String receiptKey);

    @Insert("""
            INSERT INTO fm_admin_bootstrap_receipt (
              receipt_key, user_id, username_normalized, created_at_ms
            ) VALUES (
              #{receiptKey}, #{userId}, #{usernameNormalized}, #{createdAtMs}
            )
            """)
    int insert(AdminBootstrapReceipt receipt);
}
