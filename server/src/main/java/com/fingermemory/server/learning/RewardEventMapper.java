package com.fingermemory.server.learning;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RewardEventMapper {
    @Insert("""
            INSERT INTO fm_reward_event (
              owner_id, event_type, event_key, reward_eligible, created_at_ms, payload_json
            ) VALUES (
              #{ownerId}, #{eventType}, #{eventKey}, #{rewardEligible}, #{createdAtMs},
              CAST(#{payloadJson} AS JSON)
            )
            """)
    int insert(@Param("ownerId") String ownerId,
               @Param("eventType") String eventType,
               @Param("eventKey") String eventKey,
               @Param("rewardEligible") boolean rewardEligible,
               @Param("createdAtMs") long createdAtMs,
               @Param("payloadJson") String payloadJson);

    @Select("""
            SELECT event_type AS eventType, event_key AS eventKey,
                   reward_eligible AS rewardEligible, created_at_ms AS createdAtMs,
                   payload_json AS payloadJson
            FROM fm_reward_event
            WHERE owner_id = #{ownerId}
            ORDER BY created_at_ms ASC, event_type ASC, event_key ASC
            """)
    List<RewardEventRow> listByOwner(@Param("ownerId") String ownerId);

    @Delete("""
            DELETE FROM fm_reward_event
            WHERE owner_id = #{ownerId}
            """)
    int deleteByOwner(@Param("ownerId") String ownerId);

    class RewardEventRow {
        private String eventType;
        private String eventKey;
        private boolean rewardEligible;
        private long createdAtMs;
        private String payloadJson;

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getEventKey() {
            return eventKey;
        }

        public void setEventKey(String eventKey) {
            this.eventKey = eventKey;
        }

        public boolean isRewardEligible() {
            return rewardEligible;
        }

        public void setRewardEligible(boolean rewardEligible) {
            this.rewardEligible = rewardEligible;
        }

        public long getCreatedAtMs() {
            return createdAtMs;
        }

        public void setCreatedAtMs(long createdAtMs) {
            this.createdAtMs = createdAtMs;
        }

        public String getPayloadJson() {
            return payloadJson;
        }

        public void setPayloadJson(String payloadJson) {
            this.payloadJson = payloadJson;
        }
    }
}
