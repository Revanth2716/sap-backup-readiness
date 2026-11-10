package com.revzz.sap.readiness.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The REST API.
 *
 * <p>Read-only, and authenticated - the data describes where a production
 * landscape's recovery position stands, so it is not served to anyone who
 * asks.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("the health check is open")
    void healthIsOpen() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"status\":\"UP\"")))
                .andExpect(jsonPath("$.systems").value(5));
    }

    @Test
    @DisplayName("the readiness data needs a session")
    void readinessNeedsASession() throws Exception {
        mockMvc.perform(get("/api/v1/readiness"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("authentication required")));
    }

    @Test
    @DisplayName("the readiness data is served to a signed-in caller")
    void readinessIsServedToASignedInCaller() throws Exception {
        mockMvc.perform(get("/api/v1/readiness").session(sessionFor("OPS_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(content().string(containsString("PRD")))
                .andExpect(content().string(containsString("BACKUP_FAILED")));
    }

    @Test
    @DisplayName("one system can be read on its own")
    void oneSystemCanBeRead() throws Exception {
        mockMvc.perform(get("/api/v1/systems/SOL").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"score\":100")))
                .andExpect(content().string(containsString("\"severity\":\"INFO\"")));
    }

    @Test
    @DisplayName("an unknown system is a 404 with a JSON body")
    void unknownSystemIsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/systems/NOPE").session(sessionFor("ANALYST")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(containsString("no such system")));
    }

    @Test
    @DisplayName("the findings list is served")
    void findingsAreServed() throws Exception {
        mockMvc.perform(get("/api/v1/findings").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(content().string(containsString("recommendation")));
    }

    @Test
    @DisplayName("the capacity forecasts are served")
    void capacityIsServed() throws Exception {
        mockMvc.perform(get("/api/v1/capacity").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(content().string(containsString("daysToFull")));
    }

    @Test
    @DisplayName("the report is served as markdown")
    void reportIsServed() throws Exception {
        mockMvc.perform(get("/api/v1/report.md").session(sessionFor("ANALYST")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("# Continuity readiness report")));
    }

    @Test
    @DisplayName("the report download needs a session too")
    void reportDownloadNeedsASession() throws Exception {
        mockMvc.perform(get("/api/v1/report.md"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("signing in with the demo password works, and a wrong password does not")
    void signInWorks() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", "continuity.lead")
                        .param("password", "demo1234"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/login")
                        .param("username", "continuity.lead")
                        .param("password", "wrong"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/login?error=invalid"));
    }

    @Test
    @DisplayName("the API never returns a stack trace")
    void apiNeverLeaksAStackTrace() throws Exception {
        mockMvc.perform(get("/api/v1/systems/NOPE").session(sessionFor("ANALYST")))
                .andExpect(content().string(not(containsString("at com.revzz"))))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    private MockHttpSession sessionFor(String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CurrentUser.SESSION_ATTRIBUTE, role);
        session.setAttribute("DISPLAY_NAME", role);
        return session;
    }
}
