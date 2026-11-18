package com.fingermemory.server.content;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.config.AppProperties;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class ContentPackageService {
    private static final Pattern IDEMPOTENCY_KEY =
            Pattern.compile("^[A-Za-z0-9_.:-]{3,128}$");

    private final ContentPackageValidator validator;
    private final ContentPackageMapper mapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Path packageRoot;
    private final String engineVersion;

    public ContentPackageService(
            ContentPackageValidator validator,
            ContentPackageMapper mapper,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            Clock clock,
            AppProperties appProperties) {
        this.validator = validator;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.packageRoot = Path.of(appProperties.content().packageRoot())
                .toAbsolutePath()
                .normalize();
        this.engineVersion = appProperties.content().engineVersion();
    }

    public ContentPackageDtos.Preview preview(MultipartFile file) {
        Path workspace = createWorkspace();
        try {
            ValidatedContentPackage validated = validate(file, workspace);
            return previewOf(validated);
        } finally {
            deleteRecursively(workspace);
        }
    }

    public ContentPackageDtos.InstallResult install(
            String ownerId,
            String idempotencyKey,
            MultipartFile file) {
        requireIdempotencyKey(idempotencyKey);
        Path workspace = createWorkspace();
        Path finalDirectory = null;
        String fingerprint = null;
        ValidatedContentPackage validated = null;
        try {
            validated = validate(file, workspace);
            final ValidatedContentPackage packageData = validated;
            fingerprint = validated.contentHash();
            ContentInstallationReceiptRow existingReceipt =
                    mapper.findReceipt(ownerId, idempotencyKey);
            if (existingReceipt != null) {
                if (!fingerprint.equals(existingReceipt.requestFingerprint())) {
                    throw conflict(
                            "CONTENT_IDEMPOTENCY_CONFLICT",
                            "同一幂等键不能用于不同内容包");
                }
                return objectMapper.readValue(
                        existingReceipt.responseJson(),
                        ContentPackageDtos.InstallResult.class);
            }

            ContentInstallationRow sameContent =
                    mapper.findByHash(ownerId, validated.contentHash());
            if (sameContent != null) {
                ContentPackageDtos.InstallResult duplicate = result(
                        "duplicate",
                        toView(sameContent));
                insertReceipt(ownerId, idempotencyKey, fingerprint, sameContent.id(), duplicate);
                return duplicate;
            }
            ContentInstallationRow sameVersion = mapper.findByVersion(
                    ownerId,
                    validated.namespace(),
                    validated.bookId(),
                    validated.contentVersion());
            if (sameVersion != null) {
                throw conflict(
                        "CONTENT_VERSION_CONFLICT",
                        "相同包版本已安装且内容哈希不同，请递增 contentVersion");
            }

            finalDirectory = finalDirectory(ownerId, validated.contentHash());
            if (!Files.isDirectory(finalDirectory)) {
                try {
                    Files.createDirectories(finalDirectory.getParent());
                    moveDirectory(validated.extractedDirectory(), finalDirectory);
                } catch (IOException exception) {
                    if (!Files.isDirectory(finalDirectory)) {
                        throw new ApiException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "CONTENT_STORAGE_FAILED",
                                "内容资源存储失败");
                    }
                }
            }
            String storageRelativePath =
                    packageRoot.relativize(finalDirectory).toString().replace('\\', '/');
            long now = clock.instant().toEpochMilli();
            ContentInstallationRow row = row(
                    ownerId,
                    validated,
                    storageRelativePath,
                    now);
            ContentPackageDtos.InstallResult installed = transactionTemplate.execute(status -> {
                mapper.insert(row);
                for (ContentPackageDtos.AssetView asset : packageData.assets()) {
                    mapper.insertAsset(new ContentAssetRow(
                            ownerId,
                            row.id(),
                            asset.path(),
                            asset.sha256(),
                            asset.mime(),
                            asset.byteSize(),
                            asset.kind(),
                            asset.license(),
                            now));
                }
                ContentPackageDtos.InstallResult response =
                        result("installed", toView(row));
                insertReceipt(
                        ownerId,
                        idempotencyKey,
                        packageData.contentHash(),
                        row.id(),
                        response);
                return response;
            });
            return installed;
        } catch (RuntimeException exception) {
            if (fingerprint != null) {
                ContentInstallationReceiptRow receipt =
                        mapper.findReceipt(ownerId, idempotencyKey);
                if (receipt != null && fingerprint.equals(receipt.requestFingerprint())) {
                    return objectMapper.readValue(
                            receipt.responseJson(),
                            ContentPackageDtos.InstallResult.class);
                }
                ContentInstallationRow sameContent =
                        mapper.findByHash(ownerId, fingerprint);
                if (sameContent != null) {
                    return result("duplicate", toView(sameContent));
                }
                if (validated != null) {
                    ContentInstallationRow sameVersion = mapper.findByVersion(
                            ownerId,
                            validated.namespace(),
                            validated.bookId(),
                            validated.contentVersion());
                    if (sameVersion != null) {
                        throw conflict(
                                "CONTENT_VERSION_CONFLICT",
                                "相同包版本已安装且内容哈希不同，请递增 contentVersion");
                    }
                }
            }
            throw exception;
        } finally {
            deleteRecursively(workspace);
        }
    }

    public List<ContentPackageDtos.Installation> listOwned(String ownerId, int limit) {
        if (limit < 1 || limit > 100) {
            throw validation("limit 必须在 1 到 100 之间");
        }
        return mapper.listOwned(ownerId, limit).stream().map(this::toView).toList();
    }

    public ContentPackageDtos.Installation getOwned(String ownerId, String installationId) {
        ContentInstallationRow row = mapper.findById(ownerId, installationId);
        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CONTENT_INSTALLATION_NOT_FOUND",
                    "内容安装不存在");
        }
        return toView(row);
    }

    public InstalledPackage installedPackage(String ownerId, String installationId) {
        ContentInstallationRow row = mapper.findById(ownerId, installationId);
        if (row == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CONTENT_INSTALLATION_NOT_FOUND",
                    "内容安装不存在");
        }
        Path root = packageRoot.resolve(row.storageRelativePath()).normalize();
        if (!root.startsWith(packageRoot) || !Files.isDirectory(root)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "CONTENT_RESOURCE_UNAVAILABLE",
                    "内容包资源不可用");
        }
        return new InstalledPackage(row, root);
    }

    public AssetResource asset(
            String ownerId,
            String installationId,
            String assetPath) {
        ContentInstallationRow installation = mapper.findById(ownerId, installationId);
        if (installation == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CONTENT_INSTALLATION_NOT_FOUND",
                    "内容安装不存在");
        }
        String normalizedAssetPath = normalizeAssetPath(assetPath);
        ContentAssetRow asset = mapper.findAsset(ownerId, installationId, normalizedAssetPath);
        if (asset == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CONTENT_ASSET_NOT_FOUND",
                    "内容资源不存在");
        }
        Path file = packageRoot
                .resolve(installation.storageRelativePath())
                .resolve(normalizedAssetPath)
                .normalize();
        Path installationRoot = packageRoot
                .resolve(installation.storageRelativePath())
                .normalize();
        if (!file.startsWith(installationRoot) || !Files.isRegularFile(file)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "CONTENT_ASSET_NOT_FOUND",
                    "内容资源不存在");
        }
        return new AssetResource(new FileSystemResource(file), asset.mime());
    }

    private ValidatedContentPackage validate(MultipartFile file, Path workspace) {
        if (file == null || file.isEmpty()) {
            throw validation("请选择 .fmbook 内容包");
        }
        try {
            return validator.validate(
                    file.getInputStream(),
                    file.getSize(),
                    safeFileName(file.getOriginalFilename()),
                    workspace,
                    engineVersion);
        } catch (IOException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "CONTENT_PACKAGE_INVALID",
                    "无法读取上传内容包");
        }
    }

    private ContentPackageDtos.Preview previewOf(ValidatedContentPackage validated) {
        return new ContentPackageDtos.Preview(
                validated.fileName(),
                validated.title(),
                validated.namespace(),
                validated.bookId(),
                validated.contentVersion(),
                validated.contentHash(),
                validated.engineVersion(),
                validated.entryNodeId(),
                validated.wordCount(),
                validated.chapterCount(),
                validated.nodeCount(),
                validated.characterCount(),
                validated.cardCount(),
                validated.encounterCount(),
                validated.assets().size(),
                List.of());
    }

    private ContentInstallationRow row(
            String ownerId,
            ValidatedContentPackage validated,
            String storageRelativePath,
            long now) {
        return new ContentInstallationRow(
                ownerId,
                UUID.randomUUID().toString(),
                validated.namespace(),
                validated.bookId(),
                validated.contentVersion(),
                validated.contentHash(),
                validated.title(),
                validated.entryNodeId(),
                validated.engineVersion(),
                validated.schemaVersion(),
                validated.manifestJson(),
                storageRelativePath,
                validated.wordCount(),
                validated.chapterCount(),
                validated.nodeCount(),
                validated.characterCount(),
                validated.cardCount(),
                validated.encounterCount(),
                validated.assets().size(),
                "installed",
                now,
                now);
    }

    private ContentPackageDtos.Installation toView(ContentInstallationRow row) {
        return new ContentPackageDtos.Installation(
                row.id(),
                row.namespaceId(),
                row.bookId(),
                row.contentVersion(),
                row.contentHash(),
                row.title(),
                row.entryNodeId(),
                row.status(),
                row.wordCount(),
                row.chapterCount(),
                row.nodeCount(),
                row.characterCount(),
                row.cardCount(),
                row.encounterCount(),
                row.assetCount(),
                row.installedAtMs());
    }

    private ContentPackageDtos.InstallResult result(
            String outcome,
            ContentPackageDtos.Installation installation) {
        return new ContentPackageDtos.InstallResult(outcome, installation);
    }

    private void insertReceipt(
            String ownerId,
            String idempotencyKey,
            String fingerprint,
            String installationId,
            ContentPackageDtos.InstallResult response) {
        mapper.insertReceipt(new ContentInstallationReceiptRow(
                ownerId,
                idempotencyKey,
                fingerprint,
                installationId,
                objectMapper.writeValueAsString(response),
                clock.instant().toEpochMilli()));
    }

    private Path createWorkspace() {
        try {
            Files.createDirectories(packageRoot.resolve(".staging"));
            return Files.createTempDirectory(packageRoot.resolve(".staging"), "fmbook-");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create content package staging directory", exception);
        }
    }

    private Path finalDirectory(String ownerId, String contentHash) {
        return packageRoot.resolve(ownerId).resolve(contentHash).normalize();
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
    }

    private static String safeFileName(String original) {
        String value = original == null ? "package.fmbook" : original.replace('\\', '/');
        int slash = value.lastIndexOf('/');
        String fileName = slash >= 0 ? value.substring(slash + 1) : value;
        if (fileName.length() > 255) {
            throw validation("文件名超过 255 字符");
        }
        return fileName;
    }

    private static String normalizeAssetPath(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validation("资源路径不能为空");
        }
        String value = raw.replace('\\', '/');
        Path normalized = Path.of(value).normalize();
        String path = normalized.toString().replace('\\', '/');
        if (path.startsWith("../") || path.equals("..") || path.startsWith("/")) {
            throw validation("资源路径无效");
        }
        return path;
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null
                || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw validation("Idempotency-Key 格式无效");
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
            // Staging cleanup is best effort. Content is only visible after the
            // database transaction commits and always resolves by owner/hash.
        }
    }

    private static ApiException validation(String message) {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "CONTENT_PACKAGE_VALIDATION_FAILED",
                message);
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    public record AssetResource(Resource resource, String mime) {
    }

    public record InstalledPackage(ContentInstallationRow installation, Path root) {
    }
}
