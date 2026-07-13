package com.example.smartmall;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper objectMapper;

  @Test
  void registerAndLoginReturnsTokens() throws Exception {
    String username = unique("auth_ok");
    register(username, "13810000001")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.userId").exists())
        .andExpect(jsonPath("$.username").value(username));

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("username", username, "password", "secret123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
        .andExpect(jsonPath("$.refreshToken", not(blankOrNullString())))
        .andExpect(jsonPath("$.userInfo.username").value(username));
  }

  @Test
  void duplicateUsernameRegisterReturnsConflict() throws Exception {
    String username = unique("dup");
    register(username, "13810000002").andExpect(status().isCreated());

    mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("username", username, "password", "secret123", "phone", "13810000003"))))
        .andExpect(status().isConflict());
  }

  @Test
  void wrongPasswordLoginReturnsUnauthorized() throws Exception {
    String username = unique("wrong_pwd");
    register(username, "13810000004").andExpect(status().isCreated());

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("username", username, "password", "bad-pass"))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void refreshTokenIssuesNewAccessToken() throws Exception {
    String username = unique("refresh");
    register(username, "13810000005").andExpect(status().isCreated());
    String body = mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("username", username, "password", "secret123"))))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    JsonNode login = objectMapper.readTree(body);

    mvc.perform(post("/api/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(json(Map.of("refreshToken", login.get("refreshToken").asText()))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken", not(blankOrNullString())));
  }

  private org.springframework.test.web.servlet.ResultActions register(String username, String phone) throws Exception {
    return mvc.perform(post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(json(Map.of("username", username, "password", "secret123", "phone", phone))));
  }

  private String json(Object value) throws Exception {
    return objectMapper.writeValueAsString(value);
  }

  private String unique(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
  }
}
