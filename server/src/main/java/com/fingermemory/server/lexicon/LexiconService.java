package com.fingermemory.server.lexicon;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.lexicon.LexiconDtos.CreateLexiconRequest;
import com.fingermemory.server.lexicon.LexiconDtos.UpdateLexiconRequest;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LexiconService {
    private final LexiconMapper lexiconMapper;

    public LexiconService(LexiconMapper lexiconMapper) {
        this.lexiconMapper = lexiconMapper;
    }

    public List<Lexicon> list(String ownerId, boolean includeArchived, int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "limit 必须在 1 到 100 之间");
        }
        return includeArchived
                ? lexiconMapper.listIncludingArchived(ownerId, limit)
                : lexiconMapper.listActive(ownerId, limit);
    }

    public Lexicon get(String ownerId, String id) {
        Lexicon lexicon = lexiconMapper.findByIdAndOwner(id, ownerId);
        if (lexicon == null) {
            throw notFound();
        }
        return lexicon;
    }

    @Transactional
    public Lexicon create(String ownerId, CreateLexiconRequest request) {
        long now = Instant.now().toEpochMilli();
        Lexicon lexicon = new Lexicon();
        lexicon.setId(UUID.randomUUID().toString());
        lexicon.setOwnerId(ownerId);
        lexicon.setImportBatchId("native");
        lexicon.setName(request.name().trim());
        lexicon.setNormalizedName(normalize(request.name()));
        lexicon.setDescription(request.description() == null ? "" : request.description().trim());
        lexicon.setSource("user");
        lexicon.setArchivedAtMs(null);
        lexicon.setCreatedAtMs(now);
        lexicon.setUpdatedAtMs(now);
        lexicon.setVersion(0);
        try {
            lexiconMapper.insert(lexicon);
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return lexicon;
    }

    @Transactional
    public Lexicon update(String ownerId, String id, UpdateLexiconRequest request) {
        Lexicon existing = get(ownerId, id);
        if (existing.getArchivedAtMs() != null) {
            throw new ApiException(HttpStatus.CONFLICT, "LEXICON_ARCHIVED", "已归档词库不能更新");
        }
        Lexicon candidate = new Lexicon();
        candidate.setId(id);
        candidate.setOwnerId(ownerId);
        candidate.setName(request.name().trim());
        candidate.setNormalizedName(normalize(request.name()));
        candidate.setDescription(request.description() == null ? "" : request.description().trim());
        candidate.setUpdatedAtMs(Instant.now().toEpochMilli());
        candidate.setVersion(request.version());
        try {
            if (lexiconMapper.update(candidate) == 0) {
                throw new ApiException(HttpStatus.CONFLICT, "LEXICON_VERSION_CONFLICT", "词库已在其他客户端更新");
            }
        } catch (DuplicateKeyException exception) {
            throw nameConflict();
        }
        return get(ownerId, id);
    }

    @Transactional
    public Lexicon setArchived(String ownerId, String id, boolean archived, long version) {
        Lexicon candidate = new Lexicon();
        candidate.setId(id);
        candidate.setOwnerId(ownerId);
        candidate.setArchivedAtMs(archived ? Instant.now().toEpochMilli() : null);
        candidate.setUpdatedAtMs(Instant.now().toEpochMilli());
        candidate.setVersion(version);
        if (lexiconMapper.updateArchive(candidate) == 0) {
            if (lexiconMapper.findByIdAndOwner(id, ownerId) == null) {
                throw notFound();
            }
            throw new ApiException(HttpStatus.CONFLICT, "LEXICON_VERSION_CONFLICT", "词库已在其他客户端更新");
        }
        return get(ownerId, id);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "LEXICON_NOT_FOUND", "词库不存在");
    }

    private ApiException nameConflict() {
        return new ApiException(HttpStatus.CONFLICT, "LEXICON_NAME_CONFLICT", "已有同名词库");
    }
}
