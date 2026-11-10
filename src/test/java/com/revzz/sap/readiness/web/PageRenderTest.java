package com.revzz.sap.readiness.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Renders every screen.
 *
 * <p>A template can compile and the service layer can be fully tested while
 * one screen still returns HTTP 500, because a single expression in the
 * markup does not resolve. This test renders each page the way a browser
 * would, so that class of bug fails the build.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PageRenderTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} renders")
    @ValueSource(strings = {
            "/",
            "/systems/PRD",
            "/systems/DEV",
            "/systems/BW",
            "/systems/QA",
            "/systems/SOL",
            "/report",
    })
    @DisplayName("every screen renders for the continuity lead")
    void everyScreenRenders(String path) throws Exception {
        mockMvc.perform(get(path).session(sessionFor("OPS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Whitelabel Error Page"))))
                .andExpect(content().string(not(containsString("TemplateProcessingException"))));
    }

    @Test
    @DisplayName("the login screen renders")
    void loginRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign in")));
    }

    @Test
    @DisplayName("the dashboard shows the readiness chart and the landscape")
    void dashboardShowsTheChart() throws Exception {
        mockMvc.perform(get("/").session(sessionFor("OPS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("PRD")))
                .andExpect(content().string(containsString("Continuity readiness")));
    }

    @Test
    @DisplayName("the system screen shows the capacity chart and the findings")
    void systemScreenShowsTheFindings() throws Exception {
        mockMvc.perform(get("/systems/BW").session(sessionFor("OPS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("DATABASE SIZE")))
                .andExpect(content().string(containsString("RESTORE_TEST_STALE")));
    }

    @Test
    @DisplayName("the report screen renders the markdown")
    void reportRenders() throws Exception {
        mockMvc.perform(get("/report").session(sessionFor("OPS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Continuity readiness report")));
    }

    @Test
    @DisplayName("an unknown system shows the error page, not a stack trace")
    void unknownSystemShowsErrorPage() throws Exception {
        mockMvc.perform(get("/systems/NOPE").session(sessionFor("OPS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Something went wrong")));
    }

    @ParameterizedTest(name = "the viewer cannot open {0}")
    @ValueSource(strings = {"/report"})
    @DisplayName("the viewer role is blocked from the report")
    void viewerIsBlocked(String path) throws Exception {
        mockMvc.perform(get(path).session(sessionFor("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an anonymous visitor is sent to the login page")
    void anonymousIsSentToLogin() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> {
                    String location = result.getResponse().getRedirectedUrl();
                    org.junit.jupiter.api.Assertions.assertNotNull(location);
                    org.junit.jupiter.api.Assertions.assertTrue(location.contains("/login"),
                            "expected a redirect to the login page but got " + location);
                });
    }

    @Test
    @DisplayName("the analyst may read the report but may not record a restore test")
    void analystCannotRecordARestoreTest() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/systems/PRD/restore-test")
                .param("result", "OK")
                .param("durationMinutes", "60")
                .param("performedBy", "Analyst")
                .session(sessionFor("ANALYST")))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession sessionFor(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUser.SESSION_ATTRIBUTE, role);
        session.setAttribute("DISPLAY_NAME", role);
        return session;
    }
}
