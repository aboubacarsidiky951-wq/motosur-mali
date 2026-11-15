package ml.motosur;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"motosur.maintenance.email=boss@test.ml", "motosur.maintenance.password=un-mot-de-passe-solide"})
@AutoConfigureMockMvc
class MaintenanceFlowTest {

    @Autowired MockMvc mvc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
    }

    @Test
    void maintenanceApiRequiresLogin() throws Exception {
        mvc.perform(get("/api/maintenance/overview")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"boss@test.ml\",\"password\":\"mauvais-mot-de-passe\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void loginThenOverview() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/api/auth/login").session(session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"login\":\"boss@test.ml\",\"password\":\"un-mot-de-passe-solide\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("MAINTENANCE"));
        mvc.perform(get("/api/maintenance/overview").session(session))
            .andExpect(status().isOk()).andExpect(jsonPath("$.database.ok").value(true));
    }
}
