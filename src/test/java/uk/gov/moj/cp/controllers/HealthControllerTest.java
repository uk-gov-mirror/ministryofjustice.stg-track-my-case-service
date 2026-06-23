package uk.gov.moj.cp.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.moj.cp.config.ApiPaths.PATH_API_HEALTH;

public class HealthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController()).build();
    }

    @Test
    @DisplayName("GET /api/health should return 200 OK")
    void testGetHealth_ShouldReturnOk() throws Exception {
        mockMvc.perform(get(PATH_API_HEALTH))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /api/health should return body UP")
    void testGetHealth_ShouldReturnUpBody() throws Exception {
        mockMvc.perform(get(PATH_API_HEALTH))
            .andExpect(content().string("UP"));
    }
}
