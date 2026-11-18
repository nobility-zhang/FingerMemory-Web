package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fingermemory.server.admin.AdminBootstrapProperties;
import com.fingermemory.server.admin.DefaultAdminBootstrapService;
import com.fingermemory.server.auth.AuthDtos.RegisterRequest;
import com.fingermemory.server.user.UserService;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGmIT {
    private static final String BOOTSTRAP_PASSWORD = "bootstrap horse battery";
    private static final String CHANGED_PASSWORD = "changed horse battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DefaultAdminBootstrapService bootstrapService;

    @Autowired
    private UserService userService;

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
    void bootstrapsOnceAndNeverPromotesAnExistingOrdinaryAccount() throws Exception {
        AdminBootstrapProperties properties = bootstrapProperties();
        DefaultAdminBootstrapService.BootstrapResult first = bootstrapService.bootstrap(properties);
        String firstPasswordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM fm_user WHERE username_normalized = 'admin'",
                String.class);

        DefaultAdminBootstrapService.BootstrapResult second = bootstrapService.bootstrap(properties);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_admin_bootstrap_receipt",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT password_hash FROM fm_user WHERE username_normalized = 'admin'",
                String.class)).isEqualTo(firstPasswordHash);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT must_change_password FROM fm_user WHERE username_normalized = 'admin'",
                Boolean.class)).isTrue();

        jdbcTemplate.update("UPDATE fm_user SET role = 'USER' WHERE username_normalized = 'admin'");
        bootstrapService.bootstrap(properties);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM fm_user WHERE username_normalized = 'admin'",
                String.class)).isEqualTo("USER");

        cleanDatabase();
        userService.register(new RegisterRequest(
                "admin",
                "Ordinary Admin",
                "ordinary horse battery"));
        assertThatThrownBy(() -> bootstrapService.bootstrap(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM fm_user WHERE username_normalized = 'admin'",
                String.class)).isEqualTo("USER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_admin_bootstrap_receipt",
                Integer.class)).isZero();
    }

    @Test
    void enforcesFirstPasswordChangeAdminRolesIdempotencyAndLastAdminProtection() throws Exception {
        bootstrapService.bootstrap(bootstrapProperties());
        SessionCookie session = login("admin", BOOTSTRAP_PASSWORD);

        mockMvc.perform(get("/api/v1/auth/me").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));
        mockMvc.perform(get("/api/v1/gm/context").cookie(session.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        Csrf passwordCsrf = csrf(session);
        mockMvc.perform(post("/api/v1/auth/password")
                        .cookie(passwordCsrf.cookie())
                        .header(passwordCsrf.headerName(), passwordCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"%s",
                                  "newPassword":"%s"
                                }
                                """.formatted(BOOTSTRAP_PASSWORD, CHANGED_PASSWORD)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/gm/context").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
        mockMvc.perform(get("/api/v1/gm/users").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].role").value("ADMIN"));

        Csrf createCsrf = csrf(session);
        MvcResult createResult = mockMvc.perform(post("/api/v1/gm/users")
                        .cookie(createCsrf.cookie())
                        .header(createCsrf.headerName(), createCsrf.token())
                        .header("Idempotency-Key", "create-test-user")
                        .header("X-Request-Id", "request-create-test-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"sandbox-user",
                                  "displayName":"Sandbox User",
                                  "password":"sandbox horse battery",
                                  "role":"USER",
                                  "accountKind":"test",
                                  "reason":"create isolated sandbox account"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountKind").value("test"))
                .andReturn();
        String createdUserId = JsonPath.read(
                createResult.getResponse().getContentAsString(),
                "$.id");

        Csrf duplicateCsrf = csrf(session);
        mockMvc.perform(post("/api/v1/gm/users")
                        .cookie(duplicateCsrf.cookie())
                        .header(duplicateCsrf.headerName(), duplicateCsrf.token())
                        .header("Idempotency-Key", "create-test-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"sandbox-user",
                                  "displayName":"Sandbox User",
                                  "password":"sandbox horse battery",
                                  "role":"USER",
                                  "accountKind":"test",
                                  "reason":"create isolated sandbox account"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(createdUserId));

        String adminId = jdbcTemplate.queryForObject(
                "SELECT id FROM fm_user WHERE username_normalized = 'admin'",
                String.class);
        long adminVersion = jdbcTemplate.queryForObject(
                "SELECT version FROM fm_user WHERE id = ?",
                Long.class,
                adminId);
        Csrf updateCsrf = csrf(session);
        mockMvc.perform(patch("/api/v1/gm/users/" + adminId)
                        .cookie(updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .header("Idempotency-Key", "demote-last-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expectedVersion":%d,
                                  "role":"USER",
                                  "reason":"attempt last admin demotion"
                                }
                                """.formatted(adminVersion)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GM_LAST_ADMIN"));

        mockMvc.perform(get("/api/v1/gm/audit").cookie(session.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].action").value("gm.user.create"))
                .andExpect(jsonPath("$.items[0].requestId")
                        .value("request-create-test-user"));
    }

    @Test
    void rejectsOrdinaryUsersAndFakeBearerCsrfBypassFromGmEndpoints() throws Exception {
        userService.register(new RegisterRequest(
                "ordinary-user",
                "Ordinary User",
                "ordinary horse battery"));
        SessionCookie session = login("ordinary-user", "ordinary horse battery");

        mockMvc.perform(get("/api/v1/gm/context").cookie(session.cookie()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(post("/api/v1/gm/users")
                        .cookie(session.cookie())
                        .header("Authorization", "Bearer fake-personal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    private AdminBootstrapProperties bootstrapProperties() {
        return new AdminBootstrapProperties(
                true,
                "admin",
                BOOTSTRAP_PASSWORD,
                "管理员");
    }

    private Csrf csrf(SessionCookie existing) throws Exception {
        var request = get("/api/v1/auth/csrf");
        if (existing != null) {
            request.cookie(existing.cookie());
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        Cookie cookie = result.getResponse().getCookie("FM_SESSION");
        if (cookie == null && existing != null) {
            cookie = existing.cookie();
        }
        if (cookie == null) {
            throw new AssertionError("FM_SESSION cookie missing");
        }
        return new Csrf(
                new SessionCookie(cookie),
                JsonPath.read(body, "$.headerName"),
                JsonPath.read(body, "$.token"));
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

    private record SessionCookie(Cookie cookie) {
    }

    private record Csrf(SessionCookie session, String headerName, String token) {
        Cookie cookie() {
            return session.cookie();
        }
    }
}
