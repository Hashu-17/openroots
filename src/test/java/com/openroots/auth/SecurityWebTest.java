package com.openroots.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.openroots.auth.dto.AuthResponse;
import com.openroots.common.exception.NotFoundException;
import com.openroots.config.SecurityConfig;
import com.openroots.device.controller.DeviceController;
import com.openroots.device.controller.DeviceCredentialController;
import com.openroots.device.dto.DeviceCredentialResponse;
import com.openroots.device.dto.DeviceCredentialSummary;
import com.openroots.device.service.DeviceCredentialService;
import com.openroots.device.service.DeviceService;
import com.openroots.project.controller.ProjectController;
import com.openroots.project.controller.ProjectMemberController;
import com.openroots.project.model.ProjectEntity;
import com.openroots.project.service.ProjectMemberService;
import com.openroots.project.service.ProjectService;
import com.openroots.telemetry.controller.TelemetryController;
import com.openroots.telemetry.service.TelemetryService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/**
 * HTTP-level checks of the security chain and error mapping. Services are mocked, so
 * role/membership rules are covered by the service tests; this verifies that the web layer
 * answers 401/403/404 correctly and passes the authenticated identity to the services.
 */
@WebMvcTest(controllers = {
        AuthController.class,
        ProjectController.class,
        ProjectMemberController.class,
        DeviceController.class,
        DeviceCredentialController.class,
        TelemetryController.class})
@Import({SecurityConfig.class, TokenProvider.class})
@TestPropertySource(properties = {
        "openroots.jwt.secret=" + SecurityWebTest.SECRET,
        "openroots.jwt.expiration=PT15M"})
class SecurityWebTest {

    static final String SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtMzItYnl0ZXMtbWluaW11bS0wMDAx";
    private static final String EMAIL = "person@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TokenProvider tokenProvider;

    @MockBean
    private AuthService authService;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private ProjectMemberService projectMemberService;

    @MockBean
    private DeviceService deviceService;

    @MockBean
    private DeviceCredentialService credentialService;

    @MockBean
    private TelemetryService telemetryService;

    private String bearer() {
        return "Bearer " + tokenProvider.createToken(EMAIL);
    }

    // --- 401: missing / invalid credentials -------------------------------------------------

    @Test
    void protectedRoutesRejectRequestsWithoutToken() throws Exception {
        UUID id = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(get("/api/v1/projects/{id}", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/{id}/devices", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/{id}/members", id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects/{id}/devices/{d}/credentials", id, id))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/telemetry")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/telemetry").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(projectService, deviceService, telemetryService, credentialService, projectMemberService);
    }

    @Test
    void garbageTamperedAndWrongSchemeTokensAreUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Basic dXNlcjpwYXNz"))
                .andExpect(status().isUnauthorized());
        String token = tokenProvider.createToken(EMAIL);
        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(projectService);
    }

    @Test
    void expiredToken() throws Exception {
        String expired = Jwts.builder()
                .subject(EMAIL)
                .issuedAt(Date.from(Instant.now().minusSeconds(3600)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)))
                .compact();

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedWithAnotherKeyIsUnauthorized() throws Exception {
        String foreign = Jwts.builder()
                .subject(EMAIL)
                .expiration(Date.from(Instant.now().plusSeconds(600)))
                .signWith(Keys.hmacShaKeyFor(new byte[48]))
                .compact();

        mockMvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + foreign))
                .andExpect(status().isUnauthorized());
    }

    // --- public auth routes -----------------------------------------------------------------

    @Test
    void registerAndLoginArePublic() throws Exception {
        when(authService.login(any())).thenReturn(new AuthResponse("jwt-value", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@example.com\",\"password\":\"long-enough-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("jwt-value"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(900));
    }

    @Test
    void invalidCredentialsReturn401WithGenericMessage() throws Exception {
        when(authService.login(any())).thenThrow(new BadCredentialsException("Invalid email or password"));

        mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"who@example.com\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void registrationValidatesInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ok@example.com\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(authService);
    }

    // --- authenticated identity reaches the services ----------------------------------------

    @Test
    void authenticatedCallerEmailIsPassedToServices() throws Exception {
        ProjectEntity project = new ProjectEntity("Mine");
        ReflectionTestUtils.setField(project, "createdAt", Instant.now());
        when(projectService.findAllForUser(EMAIL)).thenReturn(List.of(project));

        mockMvc.perform(get("/api/v1/projects").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("Mine"));
        verify(projectService).findAllForUser(EMAIL);
    }

    @Test
    void createProjectUsesCallerAsOwner() throws Exception {
        ProjectEntity project = new ProjectEntity("New");
        ReflectionTestUtils.setField(project, "createdAt", Instant.now());
        when(projectService.create(eq(EMAIL), eq("New"))).thenReturn(project);

        mockMvc.perform(post("/api/v1/projects").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"New\"}"))
                .andExpect(status().isCreated());
        verify(projectService).create(EMAIL, "New");
    }

    // --- 403 / 404 mapping ------------------------------------------------------------------

    @Test
    void viewerWriteOperationsAre403() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new AccessDeniedException("Project membership does not permit this operation"))
                .when(deviceService).delete(EMAIL, projectId, deviceId);
        when(telemetryService.ingest(eq(EMAIL), any())).thenThrow(new AccessDeniedException("denied"));

        mockMvc.perform(delete("/api/v1/projects/{p}/devices/{d}", projectId, deviceId).header("Authorization", bearer()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
        mockMvc.perform(post("/api/v1/telemetry").header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + deviceId + "\",\"capturedAt\":\"2026-01-01T00:00:00Z\","
                                + "\"payload\":{\"temperature\":21.5}}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignProjectResourcesAre404() throws Exception {
        UUID projectId = UUID.randomUUID();
        when(deviceService.findByProject(EMAIL, projectId))
                .thenThrow(new NotFoundException("Project not found: " + projectId));
        when(projectService.findByIdForUser(EMAIL, projectId))
                .thenThrow(new NotFoundException("Project not found: " + projectId));

        mockMvc.perform(get("/api/v1/projects/{id}/devices", projectId).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/{id}", projectId).header("Authorization", bearer()))
                .andExpect(status().isNotFound());
    }

    // --- device secrets ---------------------------------------------------------------------

    @Test
    void secretIsReturnedOnlyWhenCreatedAndNeverWhenListing() throws Exception {
        UUID projectId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        when(credentialService.create(eq(EMAIL), eq(projectId), eq(deviceId), eq("key"), any()))
                .thenReturn(new DeviceCredentialResponse(credentialId, "key", "plaintext-secret-value", null));
        when(credentialService.list(EMAIL, projectId, deviceId)).thenReturn(List.of(
                new DeviceCredentialSummary(credentialId, "key", Instant.now(), null, null, true)));

        mockMvc.perform(post("/api/v1/projects/{p}/devices/{d}/credentials", projectId, deviceId)
                        .header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"key\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.secret").value("plaintext-secret-value"));

        mockMvc.perform(get("/api/v1/projects/{p}/devices/{d}/credentials", projectId, deviceId)
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("key"))
                .andExpect(jsonPath("$.data[0].active").value(true))
                .andExpect(jsonPath("$.data[0].secret").doesNotExist())
                .andExpect(jsonPath("$.data[0].secretHash").doesNotExist())
                .andExpect(content().string(not(containsString("plaintext-secret-value"))));
    }
}
