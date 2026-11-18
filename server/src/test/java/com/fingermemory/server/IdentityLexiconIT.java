package com.fingermemory.server;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class IdentityLexiconIT {
    private static final String PASSWORD = "correct horse battery";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM fm_content_asset");
        jdbcTemplate.update("DELETE FROM fm_content_installation_receipt");
        jdbcTemplate.update("DELETE FROM fm_content_installation");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION_ATTRIBUTES");
        jdbcTemplate.update("DELETE FROM SPRING_SESSION");
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
    void isolatesPrivateLexiconsAcrossSessionsAndUsers() throws Exception {
        Csrf aliceRegistrationCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(aliceRegistrationCsrf.cookie())
                        .header(aliceRegistrationCsrf.headerName(), aliceRegistrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"alice","displayName":"Alice","password":"correct horse battery"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("alice"));

        SessionCookie aliceSession = login("alice", PASSWORD);
        String lexicId = createLexicon(aliceSession, "Alice 私人词库");

        Csrf logoutCsrf = csrf(aliceSession);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(logoutCsrf.cookie())
                        .header(logoutCsrf.headerName(), logoutCsrf.token()))
                .andExpect(status().isNoContent());

        SessionCookie reloggedAlice = login("alice", PASSWORD);
        mockMvc.perform(get("/api/v1/lexicons").cookie(reloggedAlice.cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(lexicId))
                .andExpect(jsonPath("$[0].name").value("Alice 私人词库"));

        Csrf bobRegistrationCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(bobRegistrationCsrf.cookie())
                        .header(bobRegistrationCsrf.headerName(), bobRegistrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"bob","displayName":"Bob","password":"correct horse battery"}
                                """))
                .andExpect(status().isCreated());
        SessionCookie bobSession = login("bob", PASSWORD);

        mockMvc.perform(get("/api/v1/lexicons").cookie(bobSession.cookie()))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mockMvc.perform(get("/api/v1/lexicons/" + lexicId).cookie(bobSession.cookie()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEXICON_NOT_FOUND"));

        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM fm_user WHERE username_normalized = 'alice'",
                String.class);
        org.assertj.core.api.Assertions.assertThat(passwordHash)
                .isNotEqualTo(PASSWORD)
                .startsWith("$2");
        Integer ownerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fm_lexicon l JOIN fm_user u ON u.id = l.owner_id "
                        + "WHERE u.username_normalized = 'alice' AND l.id = ?",
                Integer.class,
                lexicId);
        org.assertj.core.api.Assertions.assertThat(ownerCount).isEqualTo(1);
    }

    @Test
    void requiresCsrfForRegistrationAndAuthenticationForLexicons() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"carol\",\"password\":\"correct horse battery\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/lexicons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void rejectsAnExistingSessionAfterTheAccountIsDisabled() throws Exception {
        Csrf registrationCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(registrationCsrf.cookie())
                        .header(registrationCsrf.headerName(), registrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"disabled-user","password":"correct horse battery"}
                                """))
                .andExpect(status().isCreated());
        SessionCookie session = login("disabled-user", PASSWORD);

        jdbcTemplate.update(
                "UPDATE fm_user SET status = 'disabled' WHERE username_normalized = 'disabled-user'");

        mockMvc.perform(get("/api/v1/auth/me").cookie(session.cookie()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void rejectsPasswordsLongerThanTheBcryptByteLimit() throws Exception {
        Csrf registrationCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(registrationCsrf.cookie())
                        .header(registrationCsrf.headerName(), registrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"long-password","password":"%s"}
                                """.formatted("汉".repeat(25))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_TOO_LONG"));
    }

    @Test
    void changesThePasswordAndRejectsTheOldCredential() throws Exception {
        Csrf registrationCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(registrationCsrf.cookie())
                        .header(registrationCsrf.headerName(), registrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"password-user","password":"correct horse battery"}
                                """))
                .andExpect(status().isCreated());
        SessionCookie session = login("password-user", PASSWORD);

        Csrf passwordCsrf = csrf(session);
        mockMvc.perform(post("/api/v1/auth/password")
                        .cookie(passwordCsrf.cookie())
                        .header(passwordCsrf.headerName(), passwordCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword":"correct horse battery",
                                  "newPassword":"new correct horse battery"
                                }
                                """))
                .andExpect(status().isNoContent());

        Csrf logoutCsrf = csrf(session);
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(logoutCsrf.cookie())
                        .header(logoutCsrf.headerName(), logoutCsrf.token()))
                .andExpect(status().isNoContent());

        Csrf failedLoginCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/login")
                        .cookie(failedLoginCsrf.cookie())
                        .header(failedLoginCsrf.headerName(), failedLoginCsrf.token())
                        .param("username", "password-user")
                        .param("password", PASSWORD))
                .andExpect(status().isUnauthorized());
        login("password-user", "new correct horse battery");
    }

    @Test
    void enforcesLexiconVersionsForUpdatesArchiveAndRestore() throws Exception {
        Csrf registrationCsrf = csrf(null);
        mockMvc.perform(post("/api/v1/auth/register")
                        .cookie(registrationCsrf.cookie())
                        .header(registrationCsrf.headerName(), registrationCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"version-user","password":"correct horse battery"}
                                """))
                .andExpect(status().isCreated());
        SessionCookie session = login("version-user", PASSWORD);
        String lexiconId = createLexicon(session, "版本测试词库");

        Csrf updateCsrf = csrf(session);
        mockMvc.perform(put("/api/v1/lexicons/" + lexiconId)
                        .cookie(updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"版本测试词库 2","description":"更新","version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("版本测试词库 2"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(put("/api/v1/lexicons/" + lexiconId)
                        .cookie(updateCsrf.cookie())
                        .header(updateCsrf.headerName(), updateCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"旧版本覆盖","description":"不应写入","version":0}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEXICON_VERSION_CONFLICT"));

        Csrf archiveCsrf = csrf(session);
        mockMvc.perform(post("/api/v1/lexicons/" + lexiconId + "/archive")
                        .cookie(archiveCsrf.cookie())
                        .header(archiveCsrf.headerName(), archiveCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAtMs").isNumber())
                .andExpect(jsonPath("$.version").value(2));

        Csrf restoreCsrf = csrf(session);
        mockMvc.perform(post("/api/v1/lexicons/" + lexiconId + "/restore")
                        .cookie(restoreCsrf.cookie())
                        .header(restoreCsrf.headerName(), restoreCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archivedAtMs").doesNotExist())
                .andExpect(jsonPath("$.version").value(3));

        mockMvc.perform(post("/api/v1/lexicons/" + lexiconId + "/restore")
                        .cookie(restoreCsrf.cookie())
                        .header(restoreCsrf.headerName(), restoreCsrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LEXICON_VERSION_CONFLICT"));
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
        if (cookie != null) {
            org.assertj.core.api.Assertions.assertThat(
                            result.getResponse().getHeader("Set-Cookie"))
                    .contains("HttpOnly")
                    .contains("SameSite=Lax");
        }
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
                .andExpect(jsonPath("$.username").value(username))
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("FM_SESSION");
        if (cookie == null) {
            throw new AssertionError("FM_SESSION cookie missing after login");
        }
        return new SessionCookie(cookie);
    }

    private String createLexicon(SessionCookie session, String name) throws Exception {
        Csrf csrf = csrf(session);
        MvcResult result = mockMvc.perform(post("/api/v1/lexicons")
                        .cookie(csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","description":"隔离测试"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(name))
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private record SessionCookie(Cookie cookie) {
    }

    private record Csrf(SessionCookie session, String headerName, String token) {
        Cookie cookie() {
            return session.cookie();
        }
    }
}
