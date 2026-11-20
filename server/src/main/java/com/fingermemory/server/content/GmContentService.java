package com.fingermemory.server.content;

import com.fingermemory.server.admin.GmActionReceipt;
import com.fingermemory.server.admin.GmAuditEventRow;
import com.fingermemory.server.admin.GmMapper;
import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class GmContentService {
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9_.:-]{3,128}$");
    private static final String DRAFT = "draft";
    private static final String VALIDATED = "validated";
    private static final String PUBLISHED = "published";
    private static final String ARCHIVED = "archived";

    private final GmContentMapper contentMapper;
    private final GmContentAssetMapper assetMapper;
    private final GmMapper gmMapper;
    private final ContentPackageValidator validator;
    private final GmContentPreviewService previewService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Path packageRoot;
    private final String engineVersion;

    public GmContentService(
            GmContentMapper contentMapper,
            GmContentAssetMapper assetMapper,
            GmMapper gmMapper,
            ContentPackageValidator validator,
            GmContentPreviewService previewService,
            ObjectMapper objectMapper,
            Clock clock,
            AppProperties appProperties) {
        this.contentMapper = contentMapper;
        this.assetMapper = assetMapper;
        this.gmMapper = gmMapper;
        this.validator = validator;
        this.previewService = previewService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.packageRoot = Path.of(appProperties.content().packageRoot())
                .toAbsolutePath()
                .normalize();
        this.engineVersion = appProperties.content().engineVersion();
    }

    public GmContentDtos.ContentPage list(
            String actorId,
            int limit,
            String cursor) {
        if (limit < 1 || limit > 100) {
            throw validation("limit 必须在 1 到 100 之间");
        }
        String normalizedCursor =
                cursor == null || cursor.isBlank() ? null : cursor.trim();
        List<GmContentRow> rows =
                contentMapper.listVisible(actorId, normalizedCursor, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<GmContentRow> pageRows = hasMore ? rows.subList(0, limit) : rows;
        return new GmContentDtos.ContentPage(
                pageRows.stream().map(this::toView).toList(),
                hasMore ? pageRows.get(pageRows.size() - 1).id() : null);
    }

    public List<GmContentDtos.Catalog> catalog(int limit) {
        if (limit < 1 || limit > 100) {
            throw validation("limit 必须在 1 到 100 之间");
        }
        return contentMapper.listPublished(limit).stream()
                .map(this::toCatalog)
                .toList();
    }

    public GmContentDtos.Preview preview(String actorId, String contentId) {
        GmContentRow row = requireVisible(actorId, contentId);
        Path workspace = createWorkspace();
        try {
            ValidatedContentPackage validated = validateArchive(row, workspace);
            return previewService.read(
                    row,
                    toView(row),
                    validated.extractedDirectory(),
                    assetMapper.list(contentId));
        } finally {
            deleteRecursively(workspace);
        }
    }

    public ArchiveAssetResource asset(
            String actorId,
            String contentId,
            String rawAssetPath) {
        GmContentRow row = requireVisible(actorId, contentId);
        String assetPath = normalizeAssetPath(rawAssetPath);
        GmContentAssetRow asset = assetMapper.find(contentId, assetPath);
        if (asset == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GM_CONTENT_ASSET_NOT_FOUND",
                    "草稿资源不存在");
        }
        Path root = contentRoot(row).resolve("expanded").normalize();
        Path file = root.resolve(assetPath).normalize();
        boolean valid;
        try {
            valid = Files.isDirectory(root)
                    && file.startsWith(root)
                    && Files.isRegularFile(file)
                    && Files.size(file) == asset.byteSize()
                    && fileHash(file).equals(asset.contentHash());
        } catch (IOException exception) {
            valid = false;
        }
        if (!valid) {
            throw conflict(
                    "GM_CONTENT_RESOURCE_INVALID",
                    "草稿资源与登记版本不一致");
        }
        return new ArchiveAssetResource(
                new FileSystemResource(file),
                asset.mime());
    }

    public GmContentRow requirePublished(String contentId) {
        GmContentRow row = contentMapper.findById(contentId);
        return requirePublished(row);
    }

    public GmContentRow requirePublishedForUpdate(String contentId) {
        GmContentRow row = contentMapper.findForUpdate(contentId);
        return requirePublished(row);
    }

    private GmContentRow requirePublished(GmContentRow row) {
        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CONTENT_CATALOG_NOT_FOUND",
                    "共享内容不可用");
        }
        if (!PUBLISHED.equals(row.status())) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONTENT_CATALOG_UNAVAILABLE",
                    "该共享内容未发布或已归档");
        }
        return row;
    }

    public Path archivePath(GmContentRow row) {
        Path archive = packageRoot
                .resolve(row.storageRelativePath())
                .resolve("package.fmbook")
                .normalize();
        if (!archive.startsWith(packageRoot) || !Files.isRegularFile(archive)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "GM_CONTENT_RESOURCE_INVALID",
                    "共享内容资源不可读取");
        }
        return archive;
    }

    @Transactional
    public GmContentDtos.Content upload(
            String actorId,
            String idempotencyKey,
            String requestId,
            String reason,
            MultipartFile file) {
        requireIdempotencyKey(idempotencyKey);
        requireReason(reason);
        if (file == null || file.isEmpty()) {
            throw validation("请选择 .fmbook 内容包");
        }
        String rawHash = fileHash(file);
        String fingerprint = fingerprint("upload", rawHash, reason.trim());
        GmContentDtos.Content replay =
                findReceipt(actorId, idempotencyKey, fingerprint);
        if (replay != null) {
            return replay;
        }

        Path workspace = createWorkspace();
        Path finalDirectory = null;
        try {
            ValidatedContentPackage validated = validator.validate(
                    file.getInputStream(),
                    file.getSize(),
                    safeFileName(file.getOriginalFilename()),
                    workspace,
                    engineVersion);
            GmContentRow sameHash = contentMapper.findByHash(validated.contentHash());
            if (sameHash != null) {
                if (!actorId.equals(sameHash.createdBy())
                        && (DRAFT.equals(sameHash.status())
                        || VALIDATED.equals(sameHash.status()))) {
                    throw conflict(
                            "GM_CONTENT_ALREADY_MANAGED",
                            "该内容包正由另一管理员作为草稿管理");
                }
                GmContentDtos.Content response = toView(sameHash);
                saveReceipt(actorId, idempotencyKey, fingerprint, response);
                return response;
            }
            GmContentRow sameVersion = contentMapper.findByVersion(
                    validated.namespace(),
                    validated.bookId(),
                    validated.contentVersion());
            if (sameVersion != null) {
                throw conflict(
                        "GM_CONTENT_VERSION_CONFLICT",
                        "相同内容版本已存在且内容哈希不同，请递增版本");
            }

            String contentId = UUID.randomUUID().toString();
            finalDirectory = packageRoot.resolve("gm-content").resolve(contentId).normalize();
            Files.createDirectories(finalDirectory.getParent());
            moveDirectory(workspace, finalDirectory);
            String storageRelativePath = packageRoot
                    .relativize(finalDirectory)
                    .toString()
                    .replace('\\', '/');
            long now = clock.instant().toEpochMilli();
            GmContentRow row = row(
                    actorId,
                    contentId,
                    validated,
                    storageRelativePath,
                    now);
            contentMapper.insert(row);
            for (ContentPackageDtos.AssetView asset : validated.assets()) {
                assetMapper.insert(new GmContentAssetRow(
                        contentId,
                        asset.path(),
                        asset.sha256(),
                        asset.mime(),
                        asset.byteSize(),
                        asset.kind(),
                        asset.license()));
            }
            GmContentDtos.Content response = toView(row);
            saveReceipt(actorId, idempotencyKey, fingerprint, response);
            appendAudit(actorId, "gm.content.upload", contentId, requestId, reason);
            return response;
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "GM_CONTENT_UPLOAD_FAILED",
                    "无法读取上传内容包");
        } finally {
            if (finalDirectory == null || !Files.isDirectory(finalDirectory)) {
                deleteRecursively(workspace);
            }
        }
    }

    @Transactional
    public GmContentDtos.Content act(
            String actorId,
            String contentId,
            String idempotencyKey,
            String requestId,
            GmContentDtos.ContentAction request) {
        requireIdempotencyKey(idempotencyKey);
        requireReason(request.reason());
        String fingerprint = fingerprint(
                request.action(),
                contentId,
                request.expectedContentHash(),
                request.reason().trim());
        GmContentDtos.Content replay =
                findReceipt(actorId, idempotencyKey, fingerprint);
        if (replay != null) {
            return replay;
        }
        GmContentRow row = contentMapper.findForUpdate(contentId);
        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GM_CONTENT_NOT_FOUND",
                    "内容草稿不存在");
        }
        if (!actorId.equals(row.createdBy())
                && (DRAFT.equals(row.status()) || VALIDATED.equals(row.status()))) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GM_CONTENT_NOT_FOUND",
                    "内容草稿不存在");
        }
        if (!row.contentHash().equals(request.expectedContentHash())) {
            throw conflict(
                    "GM_CONTENT_HASH_CONFLICT",
                    "内容哈希已变化，请刷新后重试");
        }
        long now = clock.instant().toEpochMilli();
        switch (request.action()) {
            case "validate" -> validateDraft(row, now);
            case "publish" -> publish(row, actorId, now);
            case "archive" -> archive(row, actorId, now);
            default -> throw validation("不支持的内容操作");
        }
        GmContentRow updated = contentMapper.findById(contentId);
        GmContentDtos.Content response = toView(updated);
        saveReceipt(actorId, idempotencyKey, fingerprint, response);
        appendAudit(
                actorId,
                "gm.content." + request.action(),
                contentId,
                requestId,
                request.reason());
        return response;
    }

    private void validateDraft(GmContentRow row, long now) {
        if (VALIDATED.equals(row.status()) || PUBLISHED.equals(row.status())) {
            return;
        }
        if (!DRAFT.equals(row.status())) {
            throw conflict(
                    "GM_CONTENT_STATUS_CONFLICT",
                    "归档内容不能再验证");
        }
        revalidate(row);
        if (contentMapper.markValidated(
                row.id(),
                row.contentHash(),
                "[]",
                now) != 1) {
            throw conflict(
                    "GM_CONTENT_STATUS_CONFLICT",
                    "内容状态已变化，请刷新后重试");
        }
    }

    private void publish(GmContentRow row, String actorId, long now) {
        if (PUBLISHED.equals(row.status())) {
            return;
        }
        if (!VALIDATED.equals(row.status())) {
            throw conflict(
                    "GM_CONTENT_NOT_VALIDATED",
                    "内容必须先通过校验才能发布");
        }
        revalidate(row);
        if (contentMapper.publish(row.id(), row.contentHash(), actorId, now) != 1) {
            throw conflict(
                    "GM_CONTENT_STATUS_CONFLICT",
                    "内容状态已变化，请刷新后重试");
        }
    }

    private void archive(GmContentRow row, String actorId, long now) {
        if (ARCHIVED.equals(row.status())) {
            return;
        }
        if (!PUBLISHED.equals(row.status())) {
            throw conflict(
                    "GM_CONTENT_NOT_PUBLISHED",
                    "只有已发布内容可以归档");
        }
        if (contentMapper.archive(row.id(), row.contentHash(), actorId, now) != 1) {
            throw conflict(
                    "GM_CONTENT_STATUS_CONFLICT",
                    "内容状态已变化，请刷新后重试");
        }
    }

    private void revalidate(GmContentRow row) {
        Path workspace = createWorkspace();
        try {
            validateArchive(row, workspace);
        } finally {
            deleteRecursively(workspace);
        }
    }

    private GmContentRow requireVisible(String actorId, String contentId) {
        GmContentRow row = contentMapper.findById(contentId);
        boolean shared = row != null
                && (PUBLISHED.equals(row.status()) || ARCHIVED.equals(row.status()));
        if (row == null || (!actorId.equals(row.createdBy()) && !shared)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "GM_CONTENT_NOT_FOUND",
                    "内容草稿不存在");
        }
        return row;
    }

    private ValidatedContentPackage validateArchive(
            GmContentRow row,
            Path workspace) {
        Path archive = archivePath(row);
        try (InputStream input = Files.newInputStream(archive)) {
            ValidatedContentPackage validated = validator.validate(
                    input,
                    Files.size(archive),
                    row.bookId() + "-" + row.contentVersion() + ".fmbook",
                    workspace,
                    engineVersion);
            if (!row.contentHash().equals(validated.contentHash())
                    || !row.namespaceId().equals(validated.namespace())
                    || !row.bookId().equals(validated.bookId())
                    || !row.contentVersion().equals(validated.contentVersion())) {
                throw conflict(
                        "GM_CONTENT_RESOURCE_INVALID",
                        "共享内容资源与登记版本不一致");
            }
            return validated;
        } catch (IOException exception) {
            throw conflict(
                    "GM_CONTENT_RESOURCE_INVALID",
                    "共享内容资源不可读取");
        }
    }

    private Path contentRoot(GmContentRow row) {
        Path root = packageRoot.resolve(row.storageRelativePath()).normalize();
        if (!root.startsWith(packageRoot) || !Files.isDirectory(root)) {
            throw conflict(
                    "GM_CONTENT_RESOURCE_INVALID",
                    "草稿资源目录不可用");
        }
        return root;
    }

    private GmContentRow row(
            String actorId,
            String contentId,
            ValidatedContentPackage validated,
            String storageRelativePath,
            long now) {
        JsonNode manifest = objectMapper.readTree(validated.manifestJson());
        return new GmContentRow(
                contentId,
                actorId,
                validated.namespace(),
                validated.bookId(),
                validated.contentVersion(),
                validated.contentHash(),
                validated.title(),
                manifest.path("seriesId").asText(validated.bookId()),
                validated.entryNodeId(),
                validated.manifestJson(),
                storageRelativePath,
                validated.wordCount(),
                validated.chapterCount(),
                validated.nodeCount(),
                validated.characterCount(),
                validated.cardCount(),
                validated.encounterCount(),
                validated.assets().size(),
                DRAFT,
                "[]",
                0,
                null,
                null,
                now,
                now,
                null,
                null);
    }

    private GmContentDtos.Content toView(GmContentRow row) {
        List<String> issues = new ArrayList<>();
        for (JsonNode issue : objectMapper.readTree(row.issuesJson())) {
            if (issue.isString()) {
                issues.add(issue.stringValue());
            }
        }
        return new GmContentDtos.Content(
                row.id(),
                row.namespaceId(),
                row.bookId(),
                row.contentVersion(),
                row.contentHash(),
                row.title(),
                row.seriesId(),
                row.entryNodeId(),
                row.wordCount(),
                row.chapterCount(),
                row.nodeCount(),
                row.characterCount(),
                row.cardCount(),
                row.encounterCount(),
                row.assetCount(),
                row.status(),
                List.copyOf(issues),
                row.version(),
                row.createdAtMs(),
                row.updatedAtMs(),
                row.publishedAtMs(),
                row.archivedAtMs());
    }

    private GmContentDtos.Catalog toCatalog(GmContentRow row) {
        return new GmContentDtos.Catalog(
                row.id(),
                row.title(),
                row.namespaceId(),
                row.bookId(),
                row.contentVersion(),
                row.contentHash(),
                row.seriesId(),
                row.entryNodeId(),
                row.wordCount(),
                row.chapterCount(),
                row.nodeCount(),
                row.characterCount(),
                row.cardCount(),
                row.encounterCount(),
                row.assetCount(),
                row.publishedAtMs());
    }

    private GmContentDtos.Content findReceipt(
            String actorId,
            String idempotencyKey,
            String fingerprint) {
        GmActionReceipt receipt =
                gmMapper.findReceiptForUpdate(actorId, idempotencyKey);
        if (receipt == null) {
            return null;
        }
        if (!fingerprint.equals(receipt.requestFingerprint())) {
            throw conflict(
                    "GM_IDEMPOTENCY_CONFLICT",
                    "同一幂等键不能用于不同请求");
        }
        return objectMapper.readValue(
                receipt.responseJson(),
                GmContentDtos.Content.class);
    }

    private void saveReceipt(
            String actorId,
            String idempotencyKey,
            String fingerprint,
            GmContentDtos.Content response) {
        gmMapper.insertReceipt(new GmActionReceipt(
                actorId,
                idempotencyKey,
                fingerprint,
                objectMapper.writeValueAsString(response),
                clock.instant().toEpochMilli()));
    }

    private void appendAudit(
            String actorId,
            String action,
            String contentId,
            String requestId,
            String reason) {
        gmMapper.insertAudit(new GmAuditEventRow(
                UUID.randomUUID().toString(),
                actorId,
                action,
                contentId,
                "content",
                requestId == null || requestId.isBlank()
                        ? UUID.randomUUID().toString()
                        : requestId.substring(0, Math.min(120, requestId.length())),
                reason.trim(),
                "success",
                clock.instant().toEpochMilli()));
    }

    private Path createWorkspace() {
        try {
            Path staging = packageRoot.resolve(".staging");
            Files.createDirectories(staging);
            return Files.createTempDirectory(staging, "gm-content-");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create GM content staging directory", exception);
        }
    }

    private String fileHash(MultipartFile file) {
        try (
                InputStream input = file.getInputStream();
                OutputStream sink = OutputStream.nullOutputStream()
        ) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input.transferTo(new java.security.DigestOutputStream(
                    sink,
                    digest));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw validation("无法读取上传内容包");
        }
    }

    private String fileHash(Path file) {
        try (
                InputStream input = Files.newInputStream(file);
                OutputStream sink = OutputStream.nullOutputStream()
        ) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            input.transferTo(new java.security.DigestOutputStream(sink, digest));
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw conflict(
                    "GM_CONTENT_RESOURCE_INVALID",
                    "草稿资源无法读取");
        }
    }

    private String fingerprint(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                digest.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static void deleteRecursively(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ignored) {
            // Staging cleanup is best effort.
        }
    }

    private static String safeFileName(String original) {
        if (original == null || original.isBlank()) {
            return "gm-content.fmbook";
        }
        String normalized = original.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1);
    }

    private static String normalizeAssetPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validation("资源路径不能为空");
        }
        Path normalized = Path.of(raw.replace('\\', '/')).normalize();
        String path = normalized.toString().replace('\\', '/');
        if (!path.startsWith("assets/")
                || path.startsWith("../")
                || path.equals("..")
                || path.startsWith("/")) {
            throw validation("资源路径无效");
        }
        return path;
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw validation("Idempotency-Key 必须为 3-128 位安全字符");
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 300) {
            throw validation("reason 不能为空且不能超过 300 字符");
        }
    }

    private static ApiException validation(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public record ArchiveAssetResource(Resource resource, String mime) {
    }

}
