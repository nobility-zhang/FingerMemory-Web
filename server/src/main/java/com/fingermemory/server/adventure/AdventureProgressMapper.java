package com.fingermemory.server.adventure;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdventureProgressMapper {
    @Select("""
            SELECT owner_id, run_id, offer_id, node_id, target_node_id,
                   candidates_json, status, selected_choice_id,
                   selected_card_definition_id, created_at_ms, updated_at_ms
            FROM fm_adventure_reward_offer
            WHERE owner_id = #{ownerId}
              AND run_id = #{runId}
              AND status = 'pending'
            ORDER BY created_at_ms DESC, offer_id ASC
            LIMIT 1
            """)
    AdventureRewardOfferRow findPendingReward(
            @Param("ownerId") String ownerId,
            @Param("runId") String runId);

    @Insert("""
            INSERT INTO fm_adventure_reward_offer (
              owner_id, run_id, offer_id, node_id, target_node_id,
              candidates_json, status, selected_choice_id,
              selected_card_definition_id, created_at_ms, updated_at_ms
            ) VALUES (
              #{row.ownerId}, #{row.runId}, #{row.offerId}, #{row.nodeId},
              #{row.targetNodeId}, CAST(#{row.candidatesJson} AS JSON),
              #{row.status}, #{row.selectedChoiceId},
              #{row.selectedCardDefinitionId}, #{row.createdAtMs},
              #{row.updatedAtMs}
            )
            """)
    int insertReward(@Param("row") AdventureRewardOfferRow row);

    @Update("""
            UPDATE fm_adventure_reward_offer
            SET status = 'claimed',
                selected_choice_id = #{choiceId},
                selected_card_definition_id = #{cardDefinitionId},
                updated_at_ms = #{updatedAtMs}
            WHERE owner_id = #{ownerId}
              AND run_id = #{runId}
              AND offer_id = #{offerId}
              AND status = 'pending'
            """)
    int claimReward(
            @Param("ownerId") String ownerId,
            @Param("runId") String runId,
            @Param("offerId") String offerId,
            @Param("choiceId") String choiceId,
            @Param("cardDefinitionId") String cardDefinitionId,
            @Param("updatedAtMs") long updatedAtMs);

    @Insert("""
            INSERT IGNORE INTO fm_adventure_character_unlock (
              owner_id, character_key, unlock_key, source_run_id,
              source_node_id, unlocked_at_ms
            ) VALUES (
              #{ownerId}, #{characterKey}, #{unlockKey}, #{runId},
              #{nodeId}, #{unlockedAtMs}
            )
            """)
    int unlockCharacter(
            @Param("ownerId") String ownerId,
            @Param("characterKey") String characterKey,
            @Param("unlockKey") String unlockKey,
            @Param("runId") String runId,
            @Param("nodeId") String nodeId,
            @Param("unlockedAtMs") long unlockedAtMs);

    @Select("""
            SELECT character_key
            FROM fm_adventure_character_unlock
            WHERE owner_id = #{ownerId}
            ORDER BY unlocked_at_ms ASC, character_key ASC
            """)
    List<String> listUnlockedCharacters(@Param("ownerId") String ownerId);

    @Select("""
            SELECT COUNT(*)
            FROM fm_adventure_character_unlock
            WHERE owner_id = #{ownerId} AND character_key = #{characterKey}
            """)
    int hasCharacter(
            @Param("ownerId") String ownerId,
            @Param("characterKey") String characterKey);

    @Insert("""
            INSERT IGNORE INTO fm_adventure_recollection (
              owner_id, recollection_key, namespace_id, book_id, chapter_id,
              title, speaker, text, source_run_id, source_node_id, unlocked_at_ms
            ) VALUES (
              #{ownerId}, #{key}, #{namespaceId}, #{bookId}, #{chapterId},
              #{title}, #{speaker}, #{text}, #{runId}, #{nodeId}, #{unlockedAtMs}
            )
            """)
    int unlockRecollection(
            @Param("ownerId") String ownerId,
            @Param("key") String key,
            @Param("namespaceId") String namespaceId,
            @Param("bookId") String bookId,
            @Param("chapterId") String chapterId,
            @Param("title") String title,
            @Param("speaker") String speaker,
            @Param("text") String text,
            @Param("runId") String runId,
            @Param("nodeId") String nodeId,
            @Param("unlockedAtMs") long unlockedAtMs);

    @Select("""
            SELECT owner_id, recollection_key, namespace_id, book_id, chapter_id,
                   title, speaker, text, source_run_id, source_node_id,
                   unlocked_at_ms
            FROM fm_adventure_recollection
            WHERE owner_id = #{ownerId}
            ORDER BY unlocked_at_ms ASC, recollection_key ASC
            LIMIT #{limit}
            """)
    List<AdventureRecollectionRow> listRecollections(
            @Param("ownerId") String ownerId,
            @Param("limit") int limit);
}
