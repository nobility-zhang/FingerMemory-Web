package com.fingermemory.server.learning;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReviewReceiptMapper {
    @Select("""
            SELECT request_fingerprint, response_json
            FROM fm_review_receipt
            WHERE owner_id = #{ownerId} AND submission_id = #{submissionId}
            """)
    ReviewReceiptRow find(@Param("ownerId") String ownerId,
                          @Param("submissionId") String submissionId);

    @Select("""
            SELECT submission_id, request_fingerprint, response_json, reviewed_at_ms
            FROM fm_review_receipt
            WHERE owner_id = #{ownerId}
            ORDER BY reviewed_at_ms ASC, submission_id ASC
            """)
    List<ReviewReceiptRow> listByOwner(@Param("ownerId") String ownerId);

    @Insert("""
            INSERT INTO fm_review_receipt (
              owner_id, submission_id, request_fingerprint, response_json, reviewed_at_ms
            ) VALUES (
              #{ownerId}, #{submissionId}, #{fingerprint}, CAST(#{responseJson} AS JSON), #{reviewedAtMs}
            )
            """)
    int insert(@Param("ownerId") String ownerId,
               @Param("submissionId") String submissionId,
               @Param("fingerprint") String fingerprint,
               @Param("responseJson") String responseJson,
               @Param("reviewedAtMs") long reviewedAtMs);

    @Delete("""
            DELETE FROM fm_review_receipt
            WHERE owner_id = #{ownerId}
            """)
    int deleteByOwner(@Param("ownerId") String ownerId);
}
