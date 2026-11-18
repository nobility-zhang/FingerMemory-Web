package com.fingermemory.server.adventure;

import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.content.ContentInstallationRow;
import com.fingermemory.server.learning.LearningDocuments;
import com.fingermemory.server.learning.LockedUserData;
import com.fingermemory.server.learning.UserDataStore;
import com.fingermemory.server.lexicon.Lexicon;
import com.fingermemory.server.lexicon.LexiconMapper;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Clock;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AdventureSessionImporter {
    private final UserDataStore userDataStore;
    private final LexiconMapper lexiconMapper;
    private final LearningDocuments documents;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AdventureSessionImporter(
            UserDataStore userDataStore,
            LexiconMapper lexiconMapper,
            LearningDocuments documents,
            ObjectMapper objectMapper,
            Clock clock) {
        this.userDataStore = userDataStore;
        this.lexiconMapper = lexiconMapper;
        this.documents = documents;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public String importWordsAndCreateSession(
            String ownerId,
            ContentInstallationRow installation,
            AdventurePackageReader.AdventurePackage content,
            boolean reduceMotion) {
        String lexiconId = deterministicId("content-lexicon", installation.id());
        Lexicon lexicon = lexiconMapper.findByIdAndOwner(lexiconId, ownerId);
        if (lexicon == null) {
            lexicon = createLexicon(ownerId, lexiconId, installation);
        }
        if (lexicon.getArchivedAtMs() != null) {
            throw new AdventureException(
                    HttpStatus.CONFLICT,
                    "ADVENTURE_LEXICON_ARCHIVED",
                    "内容包词库已归档，请先恢复");
        }
        LockedUserData locked = userDataStore.lock(ownerId);
        ArrayNode words = documents.array(locked.document(), "words");
        ArrayNode studyItems = documents.array(locked.document(), "studyItems");
        ArrayNode memberships = documents.array(locked.document(), "lexiconItems");
        Set<String> existingMemberships = new HashSet<>();
        for (ObjectNode membership : JsonSupport.objectValues(
                memberships,
                "lexiconItems")) {
            if (lexiconId.equals(membership.path("lexiconId").asText())) {
                existingMemberships.add(membership.path("studyItemId").asText());
            }
        }
        long now = clock.instant().toEpochMilli();
        int nextOrder = existingMemberships.size();
        ArrayNode queue = objectMapper.createArrayNode();
        for (AdventurePackageReader.Vocabulary vocabulary : content.vocabulary()) {
            String wordId = deterministicId(
                    "content-word",
                    installation.id() + ":" + vocabulary.wordKey());
            String studyItemId = deterministicId(
                    "content-study",
                    installation.id() + ":" + vocabulary.wordKey());
            if (documents.findStudyItem(locked.document(), studyItemId) == null) {
                ObjectNode word = words.addObject();
                word.put("id", wordId);
                word.put("term", vocabulary.term());
                word.put("meaning", vocabulary.meaning());
                word.set(
                        "acceptedAnswers",
                        jsonArray(vocabulary.acceptedAnswersJson()));
                word.set("hints", jsonArray(vocabulary.hintsJson()));
                word.put("normalizedTerm", normalizeTerm(vocabulary.term()));
                word.put("createdAt", now);
                word.put("updatedAt", now);

                ObjectNode studyItem = studyItems.addObject();
                studyItem.put("id", studyItemId);
                studyItem.put("wordEntryId", wordId);
                studyItem.put("direction", "meaning-to-spelling");
                studyItem.put("createdAt", now);
                studyItem.put("updatedAt", now);
            }
            if (!existingMemberships.contains(studyItemId)) {
                ObjectNode membership = memberships.addObject();
                membership.put("lexiconId", lexiconId);
                membership.put("studyItemId", studyItemId);
                membership.put("order", nextOrder);
                membership.set("sourceHints", jsonArray(vocabulary.hintsJson()));
                nextOrder += 1;
            }
            ObjectNode queueItem = queue.addObject();
            queueItem.put("studyItemId", studyItemId);
            queueItem.put(
                    "wordEntryId",
                    deterministicId(
                            "content-word",
                            installation.id() + ":" + vocabulary.wordKey()));
            queueItem.put("status", "pending");
            queueItem.put("source", "new");
        }
        if (queue.isEmpty()) {
            throw new AdventureException(
                    HttpStatus.CONFLICT,
                    "ADVENTURE_VOCABULARY_EMPTY",
                    "内容包没有可用的学习词条");
        }
        String sessionId = UUID.randomUUID().toString();
        ObjectNode session = documents.array(locked.document(), "practiceSessions")
                .addObject();
        session.put("id", sessionId);
        session.put("modeId", "keyboard-recall");
        session.put("status", "active");
        session.set("queue", queue);
        session.put("currentIndex", 0);
        session.put(
                "currentStudyItemId",
                queue.get(0).path("studyItemId").asText());
        session.putObject("interactions");
        session.putNull("pendingAnswer");
        session.put("speed", 1);
        session.put("reduceMotion", reduceMotion);
        session.put("createdAt", now);
        session.put("updatedAt", now);
        session.putNull("pausedAt");
        session.putNull("completedAt");
        session.put("version", 0);
        userDataStore.save(locked, locked.document());
        return sessionId;
    }

    private Lexicon createLexicon(
            String ownerId,
            String lexiconId,
            ContentInstallationRow installation) {
        long now = clock.instant().toEpochMilli();
        String name = installation.title()
                + " · 冒险 "
                + installation.contentVersion();
        Lexicon lexicon = new Lexicon();
        lexicon.setId(lexiconId);
        lexicon.setOwnerId(ownerId);
        lexicon.setImportBatchId("content:" + installation.id());
        lexicon.setName(name);
        lexicon.setNormalizedName(normalizeName(name));
        lexicon.setDescription("内容包 " + installation.namespaceId()
                + ":" + installation.bookId());
        lexicon.setSource("content");
        lexicon.setArchivedAtMs(null);
        lexicon.setCreatedAtMs(now);
        lexicon.setUpdatedAtMs(now);
        lexicon.setVersion(0);
        try {
            lexiconMapper.insert(lexicon);
            return lexicon;
        } catch (DuplicateKeyException exception) {
            Lexicon existing = lexiconMapper.findByIdAndOwner(lexiconId, ownerId);
            if (existing != null) {
                return existing;
            }
            throw new AdventureException(
                    HttpStatus.CONFLICT,
                    "ADVENTURE_LEXICON_CONFLICT",
                    "内容包词库名称与现有词库冲突");
        }
    }

    private ArrayNode jsonArray(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createArrayNode();
        }
        var value = objectMapper.readTree(raw);
        if (!value.isArray()) {
            throw new AdventureException(
                    HttpStatus.CONFLICT,
                    "ADVENTURE_CONTENT_INVALID",
                    "词条数组字段无效");
        }
        return (ArrayNode) value;
    }

    private static String deterministicId(String namespace, String value) {
        return UUID.nameUUIDFromBytes(
                (namespace + ":" + value).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private static String normalizeName(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeTerm(String value) {
        return Normalizer.normalize(value.trim(), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
    }
}
