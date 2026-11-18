package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fingermemory.server.admin.AdminBootstrapProperties;
import com.fingermemory.server.admin.DefaultAdminBootstrapService;
import com.fingermemory.server.auth.AuthDtos.RegisterRequest;
import com.fingermemory.server.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GmSandboxIT {
    private static final String BOOTSTRAP_PASSWORD = "bootstrap horse battery";
    private static final String CHANGED_PASSWORD = "changed horse battery";
    private static final String TEMPLATE = "card-battle-v1-tutorial";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DefaultAdminBootstrapService bootstrapService;

    @Autowired
    private UserService userService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM fm_content_asset");
        jdbcTemplate.update("DELETE FROM fm_content_installation_receipt");
        jdbcTemplate.update("DELETE FROM fm_content_installation");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
        jdbcTemplate.update("DELETE FROM fm_gm_sandbox_receipt");
        jdbcTemplate.update("DELETE FROM fm_gm_sandbox");
        jdbcTemplate.update("DELETE FROM fm_gm_action_receipt");
        jdbcTemplate.update("DELETE FROM fm_gm_audit_event");
        jdbcTemplate.update("DELETE FROM fm_review_receipt");
        jdbcTemplate.update("DELETE FROM fm_reward_event");
        jdbcTemplate.update("DELETE FROM fm_migration_receipt");
        jdbcTemplate.update("DELETE FROM fm_backup_receipt");
        jdbcTemplate.update("DELETE FROM fm_import_id_map");
        jdbcTemplate.update("DELETE FROM fm_admin_bootstrap_receipt");
        jdbcTemplate.update("DELETE FROM fm_user_data");
        jdbcTemplate.update("DELETE FROM fm_lexicon");
        jdbcTemplate.update("DELETE FROM fm_user_preferences");
        jdbcTemplate.update("DELETE FROM fm_user");
    }

    @Test
    void executesVersionedSandboxCommandsWithoutTouchingNormalLearningData() throws Exception {
        SessionCookie admin = bootstrapAndLoginAdmin();
        Map<String, Integer> normalDataBefore = normalDataCounts();

        MvcResult created = createSandbox(
                admin,
                "sandbox-create-1",
                TEMPLATE,
                "sandbox-create-request",
                201)
                .andExpect(jsonPath("$.scope").value("sandbox"))
                .andExpect(jsonPath("$.templateId").value(TEMPLATE))
                .andExpect(jsonPath("$.contentInstallationId").value(nullValue()))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.state.currentNodeId").value("bridge"))
                .andExpect(jsonPath("$.state.encounterId").value("bridge-guardian"))
                .andExpect(jsonPath("$.state.deck.length()").value(12))
                .andExpect(jsonPath("$.state.unlockedCharacters.length()").value(2))
                .andReturn();
        JsonNodeView sandbox = JsonNodeView.from(
                objectMapper.readTree(created.getResponse().getContentAsString()));

        createSandbox(
                admin,
                "sandbox-create-1",
                TEMPLATE,
                "sandbox-create-request",
                201)
                .andExpect(jsonPath("$.id").value(sandbox.id()))
                .andExpect(jsonPath("$.dataEpoch").value(sandbox.epoch()));
        createSandbox(
                admin,
                "sandbox-create-1",
                "different-template",
                "sandbox-create-request",
                409)
                .andExpect(jsonPath("$.code").value("GM_IDEMPOTENCY_CONFLICT"));

        mockMvc.perform(get("/api/v1/gm/sandboxes").cookie(admin.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].ownerAdminId").value(sandbox.ownerId()));

        MvcResult jumped = execute(
                admin,
                sandbox.id(),
                "sandbox-jump-1",
                "sandbox-jump-request",
                command(
                        "jump-node",
                        0,
                        sandbox.epoch(),
                        "jump to camp",
                        commandArgs().put("nodeId", "camp")),
                200)
                .andExpect(jsonPath("$.status").value("applied"))
                .andExpect(jsonPath("$.sandbox.version").value(1))
                .andExpect(jsonPath("$.sandbox.state.currentNodeId").value("camp"))
                .andExpect(jsonPath("$.sandbox.state.encounterId").value(nullValue()))
                .andReturn();
        JsonNodeView jumpedSandbox = JsonNodeView.from(
                objectMapper.readTree(jumped.getResponse().getContentAsString()).path("sandbox"));

        execute(
                admin,
                sandbox.id(),
                "sandbox-jump-1",
                "sandbox-jump-request",
                command(
                        "jump-node",
                        0,
                        sandbox.epoch(),
                        "jump to camp",
                        commandArgs().put("nodeId", "camp")),
                200)
                .andExpect(jsonPath("$.status").value("duplicate"))
                .andExpect(jsonPath("$.sandbox.version").value(1));
        execute(
                admin,
                sandbox.id(),
                "sandbox-jump-1",
                "sandbox-jump-request",
                command(
                        "jump-node",
                        0,
                        sandbox.epoch(),
                        "jump to camp",
                        commandArgs().put("nodeId", "chapter-end")),
                409)
                .andExpect(jsonPath("$.code").value("GM_SANDBOX_IDEMPOTENCY_CONFLICT"));

        execute(
                admin,
                sandbox.id(),
                "sandbox-grant-cards-1",
                "sandbox-grant-cards-request",
                command(
                        "grant-cards",
                        1,
                        sandbox.epoch(),
                        "grant one test card",
                        commandArgs().set(
                                "cardDefinitionIds",
                                objectMapper.createArrayNode().add("traveler-strike"))),
                200)
                .andExpect(jsonPath("$.sandbox.version").value(2))
                .andExpect(jsonPath("$.sandbox.state.deck.length()").value(13));
        execute(
                admin,
                sandbox.id(),
                "sandbox-unlock-1",
                "sandbox-unlock-request",
                command(
                        "unlock-character",
                        2,
                        sandbox.epoch(),
                        "unlock fina in sandbox",
                        commandArgs().put("characterKey", "fina")),
                200)
                .andExpect(jsonPath("$.sandbox.state.unlockedCharacters[2]").value("fina"));
        execute(
                admin,
                sandbox.id(),
                "sandbox-clock-fixed-1",
                "sandbox-clock-fixed-request",
                command(
                        "set-clock",
                        3,
                        sandbox.epoch(),
                        "set deterministic sandbox clock",
                        commandArgs()
                                .put("mode", "fixed")
                                .put("nowMs", 1_700_000_000_000L)),
                200)
                .andExpect(jsonPath("$.sandbox.state.clockMode").value("fixed"))
                .andExpect(jsonPath("$.sandbox.state.virtualNowMs")
                        .value(1_700_000_000_000L));
        execute(
                admin,
                sandbox.id(),
                "sandbox-clock-real-1",
                "sandbox-clock-real-request",
                command(
                        "set-clock",
                        4,
                        sandbox.epoch(),
                        "restore real sandbox clock",
                        commandArgs().put("mode", "real")),
                200)
                .andExpect(jsonPath("$.sandbox.state.clockMode").value("real"));

        execute(
                admin,
                sandbox.id(),
                "sandbox-stale-version",
                "sandbox-stale-version-request",
                command(
                        "jump-node",
                        4,
                        sandbox.epoch(),
                        "stale version must fail",
                        commandArgs().put("nodeId", "bridge")),
                409)
                .andExpect(jsonPath("$.code").value("GM_SANDBOX_VERSION_CONFLICT"));
        execute(
                admin,
                sandbox.id(),
                "sandbox-stale-epoch",
                "sandbox-stale-epoch-request",
                command(
                        "jump-node",
                        5,
                        "00000000-0000-0000-0000-000000000000",
                        "stale epoch must fail",
                        commandArgs().put("nodeId", "bridge")),
                409)
                .andExpect(jsonPath("$.code").value("GM_SANDBOX_EPOCH_CONFLICT"));

        MvcResult reset = execute(
                admin,
                sandbox.id(),
                "sandbox-reset-1",
                "sandbox-reset-request",
                command(
                        "reset-sandbox",
                        5,
                        sandbox.epoch(),
                        "reset deterministic fixture",
                        commandArgs()),
                200)
                .andExpect(jsonPath("$.sandbox.version").value(6))
                .andExpect(jsonPath("$.sandbox.state.currentNodeId").value("bridge"))
                .andExpect(jsonPath("$.sandbox.state.deck.length()").value(12))
                .andReturn();
        JsonNodeView resetSandbox = JsonNodeView.from(
                objectMapper.readTree(reset.getResponse().getContentAsString()).path("sandbox"));
        assertThat(resetSandbox.epoch()).isNotEqualTo(sandbox.epoch());

        execute(
                admin,
                sandbox.id(),
                "sandbox-reset-1",
                "sandbox-reset-request",
                command(
                        "reset-sandbox",
                        5,
                        sandbox.epoch(),
                        "reset deterministic fixture",
                        commandArgs()),
                200)
                .andExpect(jsonPath("$.status").value("duplicate"))
                .andExpect(jsonPath("$.sandbox.version").value(6))
                .andExpect(jsonPath("$.sandbox.dataEpoch").value(resetSandbox.epoch()));
        execute(
                admin,
                sandbox.id(),
                "sandbox-old-epoch-1",
                "sandbox-old-epoch-request",
                command(
                        "jump-node",
                        6,
                        sandbox.epoch(),
                        "old epoch action must not revive",
                        commandArgs().put("nodeId", "camp")),
                409)
                .andExpect(jsonPath("$.code").value("GM_SANDBOX_EPOCH_CONFLICT"));

        assertThat(normalDataCounts()).isEqualTo(normalDataBefore);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_gm_audit_event WHERE scope = 'sandbox'",
                Integer.class)).isEqualTo(12);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_gm_audit_event "
                        + "WHERE request_id = 'sandbox-jump-request' AND outcome = 'success'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_gm_audit_event "
                        + "WHERE request_id = 'sandbox-stale-version-request' "
                        + "AND outcome = 'rejected'",
                Integer.class)).isEqualTo(1);
        assertThat(jumpedSandbox.version()).isEqualTo(1);
    }

    @Test
    void rejectsOrdinaryUsersFakeBearerAndCrossAdminSandboxAccess() throws Exception {
        SessionCookie admin = bootstrapAndLoginAdmin();
        MvcResult created = createSandbox(
                admin,
                "sandbox-owner-create",
                TEMPLATE,
                "sandbox-owner-create-request",
                201)
                .andReturn();
        JsonNodeView sandbox = JsonNodeView.from(
                objectMapper.readTree(created.getResponse().getContentAsString()));

        userService.register(new RegisterRequest(
                "sandbox-ordinary",
                "Sandbox Ordinary",
                "ordinary horse battery"));
        SessionCookie ordinary = login("sandbox-ordinary", "ordinary horse battery");
        mockMvc.perform(get("/api/v1/gm/sandboxes").cookie(ordinary.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/gm/sandboxes/" + sandbox.id() + "/commands")
                        .cookie(admin.cookie())
                        .header("Idempotency-Key", "missing-csrf")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                "jump-node",
                                0,
                                sandbox.epoch(),
                                "missing CSRF must fail",
                                commandArgs().put("nodeId", "camp")))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/gm/sandboxes/" + sandbox.id() + "/commands")
                        .cookie(ordinary.cookie())
                        .header("Authorization", "Bearer fake-personal-token")
                        .header("Idempotency-Key", "ordinary-command")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(command(
                                "jump-node",
                                0,
                                sandbox.epoch(),
                                "must be rejected",
                                commandArgs().put("nodeId", "camp")))))
                .andExpect(status().isForbidden());

        userService.createAccount(
                new RegisterRequest(
                        "sandbox-admin-two",
                        "Sandbox Admin Two",
                        "admin two horse battery"),
                "ADMIN",
                "standard",
                false);
        SessionCookie adminTwo = login("sandbox-admin-two", "admin two horse battery");
        mockMvc.perform(get("/api/v1/gm/sandboxes").cookie(adminTwo.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        execute(
                adminTwo,
                sandbox.id(),
                "cross-admin-command",
                "cross-admin-command-request",
                command(
                        "jump-node",
                        0,
                        sandbox.epoch(),
                        "cross-admin access must fail",
                        commandArgs().put("nodeId", "camp")),
                404)
                .andExpect(jsonPath("$.code").value("GM_SANDBOX_NOT_FOUND"));
    }

    @Test
    void serializesConcurrentIdenticalSandboxCommands() throws Exception {
        SessionCookie admin = bootstrapAndLoginAdmin();
        MvcResult created = createSandbox(
                admin,
                "sandbox-concurrent-create",
                TEMPLATE,
                "sandbox-concurrent-create-request",
                201)
                .andReturn();
        JsonNodeView sandbox = JsonNodeView.from(
                objectMapper.readTree(created.getResponse().getContentAsString()));
        Csrf csrf = csrf(admin);
        ObjectNode body = command(
                "jump-node",
                0,
                sandbox.epoch(),
                "concurrent jump",
                commandArgs().put("nodeId", "camp"));
        String bodyJson = objectMapper.writeValueAsString(body);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<MvcResult> first = executor.submit(() -> executeAfterLatch(
                    start,
                    admin,
                    csrf,
                    "sandbox-concurrent-jump",
                    "sandbox-concurrent-jump-request",
                    sandbox.id(),
                    bodyJson));
            Future<MvcResult> second = executor.submit(() -> executeAfterLatch(
                    start,
                    admin,
                    csrf,
                    "sandbox-concurrent-jump",
                    "sandbox-concurrent-jump-request",
                    sandbox.id(),
                    bodyJson));
            start.countDown();

            MvcResult firstResult = first.get(20, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(20, TimeUnit.SECONDS);
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(List.of(
                    objectMapper.readTree(firstResult.getResponse().getContentAsString())
                            .path("status").asText(),
                    objectMapper.readTree(secondResult.getResponse().getContentAsString())
                            .path("status").asText()))
                    .containsExactlyInAnyOrder("applied", "duplicate");
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM fm_gm_sandbox WHERE owner_admin_id = ? AND id = ?",
                Long.class,
                sandbox.ownerId(),
                sandbox.id())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_gm_audit_event "
                        + "WHERE action = 'gm.sandbox.jump-node' AND target = ?",
                Integer.class,
                sandbox.id())).isEqualTo(1);
    }

    private Map<String, Integer> normalDataCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : new String[]{
                "fm_user_data",
                "fm_battle",
                "fm_reward_event",
                "fm_review_receipt"}) {
            counts.put(table, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + table,
                    Integer.class));
        }
        return counts;
    }

    private SessionCookie bootstrapAndLoginAdmin() throws Exception {
        bootstrapService.bootstrap(new AdminBootstrapProperties(
                true,
                "admin",
                BOOTSTRAP_PASSWORD,
                "管理员"));
        SessionCookie session = login("admin", BOOTSTRAP_PASSWORD);
        Csrf csrf = csrf(session);
        mockMvc.perform(post("/api/v1/auth/password")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"%s",
                                  "newPassword":"%s"
                                }
                                """.formatted(BOOTSTRAP_PASSWORD, CHANGED_PASSWORD)))
                .andExpect(status().isNoContent());
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions createSandbox(
            SessionCookie session,
            String idempotencyKey,
            String templateId,
            String requestId,
            int expectedStatus) throws Exception {
        Csrf csrf = csrf(session);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("templateId", templateId);
        body.putNull("contentInstallationId");
        body.put("reason", "create isolated GM sandbox");
        return mockMvc.perform(post("/api/v1/gm/sandboxes")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions execute(
            SessionCookie session,
            String sandboxId,
            String idempotencyKey,
            String requestId,
            ObjectNode body,
            int expectedStatus) throws Exception {
        Csrf csrf = csrf(session);
        return mockMvc.perform(post(
                                "/api/v1/gm/sandboxes/" + sandboxId + "/commands")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expectedStatus));
    }

    private MvcResult executeAfterLatch(
            CountDownLatch start,
            SessionCookie session,
            Csrf csrf,
            String idempotencyKey,
            String requestId,
            String sandboxId,
            String bodyJson) throws Exception {
        start.await(10, TimeUnit.SECONDS);
        return mockMvc.perform(post(
                                "/api/v1/gm/sandboxes/" + sandboxId + "/commands")
                        .cookie(session.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .header("Idempotency-Key", idempotencyKey)
                        .header("X-Request-Id", requestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andReturn();
    }

    private ObjectNode command(
            String command,
            long expectedVersion,
            String dataEpoch,
            String reason,
            ObjectNode args) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("command", command);
        body.put("expectedVersion", expectedVersion);
        body.put("dataEpoch", dataEpoch);
        body.put("reason", reason);
        body.set("args", args);
        return body;
    }

    private ObjectNode commandArgs() {
        return objectMapper.createObjectNode();
    }

    private SessionCookie login(String username, String password) throws Exception {
        Csrf csrf = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .param("username", username)
                        .param("password", password))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("FM_SESSION");
        if (cookie == null) {
            throw new AssertionError("FM_SESSION cookie missing after login");
        }
        return new SessionCookie(cookie);
    }

    private Csrf csrf(SessionCookie existing) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (existing != null) {
            request.cookie(existing.cookie());
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("FM_SESSION");
        if (cookie == null && existing != null) {
            cookie = existing.cookie();
        }
        if (cookie == null) {
            throw new AssertionError("FM_SESSION cookie missing");
        }
        String body = result.getResponse().getContentAsString();
        return new Csrf(
                new SessionCookie(cookie),
                JsonPath.read(body, "$.headerName"),
                JsonPath.read(body, "$.token"));
    }

    private record SessionCookie(Cookie cookie) {
    }

    private record Csrf(SessionCookie session, String headerName, String token) {
        Cookie cookie() {
            return session.cookie();
        }
    }

    private record JsonNodeView(
            String id,
            String ownerId,
            String epoch,
            long version) {
        private static JsonNodeView from(tools.jackson.databind.JsonNode node) {
            return new JsonNodeView(
                    node.path("id").asText(),
                    node.path("ownerAdminId").asText(),
                    node.path("dataEpoch").asText(),
                    node.path("version").asLong());
        }
    }
}
