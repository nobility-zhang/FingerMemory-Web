package com.fingermemory.server.backup;

import com.fingermemory.server.common.ApiException;
import com.fingermemory.server.common.JsonSupport;
import com.fingermemory.server.config.AppProperties;
import com.fingermemory.server.content.ContentPackageService;
import com.fingermemory.server.content.ContentPackageValidator;
import com.fingermemory.server.content.ValidatedContentPackage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class AccountBackupResourceService {
    private static final long MAX_RESOURCE_BYTES = 20L * 1024 * 1024;
    private static final long MAX_INSTALLATION_BYTES = 200L * 1024 * 1024;

    private final ContentPackageService contentPackageService;
    private final ContentPackageValidator validator;
    private final ObjectMapper objectMapper;
    private final Path packageRoot;
    private final String engineVersion;

    public AccountBackupResourceService(
            ContentPackageService contentPackageService,
            ContentPackageValidator validator,
            ObjectMapper objectMapper,
            AppProperties appProperties) {
        this.contentPackageService = contentPackageService;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.packageRoot = Path.of(appProperties.content().packageRoot())
                .toAbsolutePath()
                .normalize();
        this.engineVersion = appProperties.content().engineVersion();
    }

    public ArrayNode export(String ownerId, ObjectNode serverTables) {
        ArrayNode resources = objectMapper.createArrayNode();
        for (ObjectNode installation : JsonSupport.objectValues(
                JsonSupport.requireArray(serverTables, "contentInstallations"),
                "contentInstallations")) {
            String installationId = JsonSupport.requireIdentifier(installation, "id");
            ContentPackageService.InstalledPackage installed =
                    contentPackageService.installedPackage(ownerId, installationId);
            long totalBytes = 0;
            try (Stream<Path> paths = Files.walk(installed.root())) {
                List<Path> files = paths.filter(Files::isRegularFile)
                        .sorted(Comparator.comparing(
                                path -> relative(installed.root(), path)))
                        .toList();
                for (Path file : files) {
                    if (Files.isSymbolicLink(file)) {
                        throw conflict(
                                "BACKUP_RESOURCE_INVALID",
                                "内容安装包含符号链接");
                    }
                    long byteSize = Files.size(file);
                    totalBytes = Math.addExact(totalBytes, byteSize);
                    if (totalBytes > MAX_INSTALLATION_BYTES) {
                        throw conflict(
                                "BACKUP_RESOURCE_INVALID",
                                "内容安装资源超过单包备份上限");
                    }
                    ObjectNode value = resources.addObject();
                    value.put("installationId", installationId);
                    value.put("path", relative(installed.root(), file));
                    value.put("sha256", fileHash(file));
                    value.put("byteSize", byteSize);
                    value.put("dataBase64", Base64.getEncoder().encodeToString(
                            Files.readAllBytes(file)));
                }
            } catch (IOException exception) {
                throw conflict(
                        "BACKUP_RESOURCE_INVALID",
                        "无法读取内容安装资源");
            }
        }
        return resources;
    }

    public StagedResources stage(
            String ownerId,
            ObjectNode serverTables,
            JsonNode resourceNode) {
        if (resourceNode == null || !resourceNode.isArray()) {
            throw JsonSupport.validation("备份缺少 resources");
        }
        Map<String, ObjectNode> installations = new LinkedHashMap<>();
        for (ObjectNode row : JsonSupport.objectValues(
                JsonSupport.requireArray(serverTables, "contentInstallations"),
                "contentInstallations")) {
            installations.put(JsonSupport.requireIdentifier(row, "id"), row);
        }
        Map<String, Map<String, ResourceValue>> resources = new LinkedHashMap<>();
        for (JsonNode item : resourceNode) {
            if (!item.isObject()) {
                throw JsonSupport.validation("备份资源必须是对象");
            }
            ObjectNode value = (ObjectNode) item;
            String installationId =
                    JsonSupport.requireIdentifier(value, "installationId");
            if (!installations.containsKey(installationId)) {
                throw JsonSupport.validation("备份资源引用了未知安装");
            }
            String path = normalizeResourcePath(JsonSupport.requireText(value, "path", 512));
            String sha256 = requireHash(value);
            long byteSize = JsonSupport.requireLong(value, "byteSize");
            if (byteSize <= 0 || byteSize > MAX_RESOURCE_BYTES) {
                throw JsonSupport.validation("备份资源大小无效");
            }
            String encoded = JsonSupport.requireText(value, "dataBase64", 28_000_000);
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(encoded);
            } catch (IllegalArgumentException exception) {
                throw JsonSupport.validation("备份资源 Base64 无效");
            }
            if (bytes.length != byteSize || !sha256(bytes).equals(sha256)) {
                throw JsonSupport.validation("备份资源哈希或大小不一致");
            }
            Map<String, ResourceValue> files =
                    resources.computeIfAbsent(installationId, ignored -> new LinkedHashMap<>());
            if (files.put(path, new ResourceValue(sha256, byteSize, bytes)) != null) {
                throw JsonSupport.validation("备份资源路径重复");
            }
        }

        Path workRoot = createWorkspace();
        List<StagedInstallation> staged = new ArrayList<>();
        try {
            for (ObjectNode installation : installations.values()) {
                String installationId =
                        JsonSupport.requireIdentifier(installation, "id");
                Map<String, ResourceValue> files = resources.get(installationId);
                if (files == null || files.isEmpty()) {
                    throw JsonSupport.validation(
                            "内容安装缺少备份资源：" + installationId);
                }
                Path expanded = workRoot.resolve("expanded").resolve(installationId);
                long totalBytes = 0;
                for (Map.Entry<String, ResourceValue> file : files.entrySet()) {
                    totalBytes = Math.addExact(totalBytes, file.getValue().byteSize());
                    if (totalBytes > MAX_INSTALLATION_BYTES) {
                        throw JsonSupport.validation("内容安装资源超过备份上限");
                    }
                    Path target = expanded.resolve(file.getKey()).normalize();
                    if (!target.startsWith(expanded)) {
                        throw JsonSupport.validation("备份资源路径越界");
                    }
                    Files.createDirectories(target.getParent());
                    Files.write(target, file.getValue().bytes());
                }
                Path archive = workRoot.resolve("package").resolve(installationId + ".fmbook");
                Files.createDirectories(archive.getParent());
                zip(expanded, archive);
                Path validationWorkspace =
                        workRoot.resolve("validate").resolve(installationId);
                ValidatedContentPackage validated;
                try (InputStream input = Files.newInputStream(archive)) {
                    validated = validator.validate(
                            input,
                            Files.size(archive),
                            installationId + ".fmbook",
                            validationWorkspace,
                            engineVersion);
                }
                requireInstallationMatch(installation, validated);
                Path finalRoot = packageRoot
                        .resolve(ownerId)
                        .resolve(validated.contentHash())
                        .normalize();
                staged.add(new StagedInstallation(
                        installationId,
                        validated.contentHash(),
                        validated.extractedDirectory(),
                        finalRoot));
            }
            return new StagedResources(workRoot, List.copyOf(staged));
        } catch (IOException exception) {
            deleteRecursively(workRoot);
            throw conflict(
                    "BACKUP_RESOURCE_INVALID",
                    "无法准备内容安装资源");
        } catch (RuntimeException exception) {
            deleteRecursively(workRoot);
            throw exception;
        }
    }

    public void publish(StagedResources stagedResources) {
        try {
            for (StagedInstallation staged : stagedResources.installations()) {
                if (Files.isDirectory(staged.finalRoot())) {
                    if (!treeHash(staged.finalRoot()).equals(staged.contentHash())) {
                        throw conflict(
                                "BACKUP_RESOURCE_CONFLICT",
                                "目标内容安装资源与备份哈希不一致");
                    }
                    continue;
                }
                Files.createDirectories(staged.finalRoot().getParent());
                moveDirectory(staged.extractedRoot(), staged.finalRoot());
            }
        } catch (IOException exception) {
            throw conflict(
                    "BACKUP_RESOURCE_INVALID",
                    "无法发布恢复后的内容资源");
        }
    }

    public void cleanup(StagedResources stagedResources) {
        deleteRecursively(stagedResources.workRoot());
    }

    public int count(JsonNode resources) {
        if (resources == null || !resources.isArray()) {
            throw JsonSupport.validation("备份缺少 resources");
        }
        return resources.size();
    }

    private void requireInstallationMatch(
            ObjectNode installation,
            ValidatedContentPackage validated) {
        if (!JsonSupport.requireText(installation, "content_hash", 64)
                .equals(validated.contentHash())
                || !JsonSupport.requireText(installation, "namespace_id", 128)
                .equals(validated.namespace())
                || !JsonSupport.requireText(installation, "book_id", 128)
                .equals(validated.bookId())
                || !JsonSupport.requireText(installation, "content_version", 32)
                .equals(validated.contentVersion())
                || installation.path("asset_count").asInt(-1) != validated.assets().size()) {
            throw JsonSupport.validation("内容安装资源与备份元数据不一致");
        }
    }

    private void zip(Path root, Path target) throws IOException {
        try (OutputStream output = Files.newOutputStream(target);
             ZipArchiveOutputStream zip = new ZipArchiveOutputStream(output);
             Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .toList()) {
                byte[] bytes = Files.readAllBytes(file);
                ZipArchiveEntry entry = new ZipArchiveEntry(relative(root, file));
                entry.setSize(bytes.length);
                zip.putArchiveEntry(entry);
                zip.write(bytes);
                zip.closeArchiveEntry();
            }
        }
    }

    private Path createWorkspace() {
        try {
            Path staging = packageRoot.resolve(".staging");
            Files.createDirectories(staging);
            return Files.createTempDirectory(staging, "account-backup-");
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create backup staging directory", exception);
        }
    }

    private String treeHash(Path root) throws IOException {
        MessageDigest digest = sha256();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> relative(root, path)))
                    .toList()) {
                if (Files.isSymbolicLink(file)) {
                    throw conflict(
                            "BACKUP_RESOURCE_INVALID",
                            "内容安装包含符号链接");
                }
                digest.update(relative(root, file).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(fileBytesHash(file));
                digest.update((byte) 0);
                digest.update(Long.toString(Files.size(file))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String fileHash(Path file) throws IOException {
        return HexFormat.of().formatHex(fileBytesHash(file));
    }

    private byte[] fileBytesHash(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        return digest.digest();
    }

    private String sha256(byte[] value) {
        return HexFormat.of().formatHex(sha256().digest(value));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String requireHash(ObjectNode value) {
        String hash = JsonSupport.requireText(value, "sha256", 64);
        if (!hash.matches("^[0-9a-f]{64}$")) {
            throw JsonSupport.validation("备份资源哈希无效");
        }
        return hash;
    }

    private String normalizeResourcePath(String raw) {
        Path path = Path.of(raw.replace('\\', '/')).normalize();
        String value = path.toString().replace('\\', '/');
        if (value.isBlank() || value.startsWith("../")
                || value.startsWith("/")
                || value.equals("..")) {
            throw JsonSupport.validation("备份资源路径无效");
        }
        return value;
    }

    private String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
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
            // Backup staging cleanup is best effort.
        }
    }

    private ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private record ResourceValue(String sha256, long byteSize, byte[] bytes) {
    }

    public record StagedResources(Path workRoot, List<StagedInstallation> installations) {
    }

    private record StagedInstallation(
            String installationId,
            String contentHash,
            Path extractedRoot,
            Path finalRoot) {
    }
}
