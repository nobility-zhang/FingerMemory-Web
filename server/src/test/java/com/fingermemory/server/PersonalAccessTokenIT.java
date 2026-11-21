package com.fingermemory.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.Instant;
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
class PersonalAccessTokenIT {
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM fm_personal_access_token");
        jdbcTemplate.update("DELETE FROM fm_content_asset");
        jdbcTemplate.update("DELETE FROM fm_content_installation_receipt");
        jdbcTemplate.update("DELETE FROM fm_content_installation");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
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
    void issuesOneTimePlaintextAndAcceptsBearerWritesWithoutCsrf() throws Exception {
        RegisteredUser user = registerAndLogin("pat-user");

        Csrf csrf = csrf(user.session());
        MvcResult created = mockMvc.perform(post("/api/v1/auth/tokens")
                        .cookie(csrf.cookie().cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CLI workstation"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token.name").value("CLI workstation"))
                .andExpect(jsonPath("$.token.prefix").value(org.hamcrest.Matchers.startsWith("fm_pat_")))
                .andExpect(jsonPath("$.token.scope").value("account"))
                .andExpect(jsonPath("$.token.tokenHash").doesNotExist())
                .andReturn();
        String plaintext = JsonPath.read(
                created.getResponse().getContentAsString(), "$.plaintextToken");
        String tokenId = JsonPath.read(
                created.getResponse().getContentAsString(), "$.token.id");
        assertThat(plaintext).startsWith("fm_pat_").hasSizeGreaterThan(40);

        mockMvc.perform(get("/api/v1/auth/tokens").cookie(user.session().cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(tokenId))
                .andExpect(jsonPath("$[0].plaintextToken").doesNotExist())
                .andExpect(jsonPath("$[0].tokenHash").doesNotExist());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + plaintext))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("pat-user"));

        MvcResult lexicon = mockMvc.perform(post("/api/v1/lexicons")
                        .header("Authorization", "Bearer " + plaintext)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Bearer write","description":"no CSRF header"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String lexiconId = JsonPath.read(
                lexicon.getResponse().getContentAsString(), "$.id");
        assertThat(lexiconId).isNotBlank();

        mockMvc.perform(post("/api/v1/lexicons")
                        .cookie(user.session().cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Cookie write","description":"CSRF required"}
                                """))
                .andExpect(status().isForbidden());

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM fm_personal_access_token WHERE id = ?",
                String.class,
                tokenId);
        assertThat(storedHash).hasSize(64).isNotEqualTo(plaintext).doesNotContain(plaintext);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_personal_access_token WHERE token_hash = ?",
                Integer.class,
                plaintext)).isZero();
    }

    @Test
    void keepsPatOutsideBrowserSessionOperationsAndGmAdministration() throws Exception {
        RegisteredUser user = registerAndLogin("pat-admin");
        jdbcTemplate.update("UPDATE fm_user SET role = 'ADMIN' WHERE id = ?", user.id());
        RegisteredUser refreshed = new RegisteredUser(user.id(), login("pat-admin"));
        String plaintext = createToken(refreshed.session(), "admin CLI");

        mockMvc.perform(get("/api/v1/auth/tokens")
                        .header("Authorization", "Bearer " + plaintext))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BROWSER_SESSION_REQUIRED"));

        mockMvc.perform(get("/api/v1/gm/sandboxes")
                        .header("Authorization", "Bearer " + plaintext))
                .andExpect(status().isForbidden());

        Csrf adminCsrf = csrf(refreshed.session());
        mockMvc.perform(get("/api/v1/gm/sandboxes")
                        .cookie(adminCsrf.cookie().cookie())
                        .header(adminCsrf.headerName(), adminCsrf.token()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/gm/sandboxes")
                        .cookie(adminCsrf.cookie().cookie())
                        .header("Authorization", "Bearer fm_pat_fake"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void revokesByOwnerRejectsExpiredAndRevokesAllOnPasswordChange() throws Exception {
        RegisteredUser alice = registerAndLogin("pat-alice");
        RegisteredUser bob = registerAndLogin("pat-bob");
        String first = createToken(alice.session(), "first");
        long expiringAt = Instant.now().plus(Duration.ofMinutes(10)).toEpochMilli();
        Csrf csrf = csrf(alice.session());
        MvcResult secondResult = mockMvc.perform(post("/api/v1/auth/tokens")
                        .cookie(csrf.cookie().cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"expiring","expiresAtMs":%d}
                                """.formatted(expiringAt)))
                .andExpect(status().isCreated())
                .andReturn();
        String expiring = JsonPath.read(
                secondResult.getResponse().getContentAsString(), "$.plaintextToken");
        String firstId = jdbcTemplate.queryForObject(
                "SELECT id FROM fm_personal_access_token WHERE owner_id = ? AND name = 'first'",
                String.class,
                alice.id());

        Csrf bobCsrf = csrf(bob.session());
        mockMvc.perform(delete("/api/v1/auth/tokens/" + firstId)
                        .cookie(bobCsrf.cookie().cookie())
                        .header(bobCsrf.headerName(), bobCsrf.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + first))
                .andExpect(status().isOk());

        jdbcTemplate.update(
                "UPDATE fm_personal_access_token SET expires_at_ms = 1 WHERE owner_id = ? AND name = 'expiring'",
                alice.id());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + expiring))
                .andExpect(status().isUnauthorized());

        Csrf revokeCsrf = csrf(alice.session());
        mockMvc.perform(delete("/api/v1/auth/tokens/" + firstId)
                        .cookie(revokeCsrf.cookie().cookie())
                        .header(revokeCsrf.headerName(), revokeCsrf.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + first))
                .andExpect(status().isUnauthorized());

        String third = createToken(alice.session(), "password rotation");
        Csrf passwordCsrf = csrf(alice.session());
        mockMvc.perform(post("/api/v1/auth/password")
                        .cookie(passwordCsrf.cookie().cookie())
                        .header(passwordCsrf.headerName(), passwordCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"correct horse battery","newPassword":"changed horse battery"}
                                """))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + third))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_personal_access_token "
                        + "WHERE owner_id = ? AND revoked_at_ms IS NULL",
                Integer.class,
                alice.id())).isZero();
    }

    private String createToken(SessionCookie session, String name) throws Exception {
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/tokens")
                        .cookie(csrf.cookie().cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.createObjectNode()
                                .put("name", name)
                                .toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.plaintextToken");
    }

    private RegisteredUser registerAndLogin(String username) throws Exception {
        Csrf registrationCsrf = csrf(null);
        MvcResult registration = mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(registrationCsrf.cookie().cookie())
                        .header(registrationCsrf.headerName(), registrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"correct horse battery"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn();
        String userId = JsonPath.read(registration.getResponse().getContentAsString(), "$.id");
        return new RegisteredUser(userId, login(username));
    }

    private SessionCookie login(String username) throws Exception {
        Csrf csrf = csrf(null);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(csrf.cookie().cookie())
                        .header(csrf.headerName(), csrf.token())
                        .param("username", username)
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        Cookie session = result.getResponse().getCookie("FM_SESSION");
        assertThat(session).isNotNull();
        return new SessionCookie(session);
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
        assertThat(cookie).isNotNull();
        ObjectNode body = (ObjectNode) objectMapper.readTree(
                result.getResponse().getContentAsString());
        return new Csrf(
                new SessionCookie(cookie),
                body.path("headerName").asText(),
                body.path("token").asText());
    }

    private record RegisteredUser(String id, SessionCookie session) {
    }

    private record SessionCookie(Cookie cookie) {
    }

    private record Csrf(SessionCookie cookie, String headerName, String token) {
    }
}
