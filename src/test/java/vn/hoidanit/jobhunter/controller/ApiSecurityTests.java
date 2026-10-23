package vn.hoidanit.jobhunter.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.hoidanit.jobhunter.domain.*;
import vn.hoidanit.jobhunter.repository.*;
import vn.hoidanit.jobhunter.service.EmailService;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiSecurityTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired CompanyRepository companies;
    @Autowired JobRepository jobs;
    @Autowired ResumeRepository resumes;
    @Autowired PermissionRepository permissions;
    @Autowired vn.hoidanit.jobhunter.util.SecurityUtil tokens;
    @Autowired org.springframework.security.oauth2.jwt.JwtDecoder decoder;
    @Autowired ObjectMapper json;
    @MockBean EmailService emails;
    User candidate;
    User admin;
    Company company;
    Job job;

    @BeforeEach
    void setup() {
        candidate = account("candidate@example.test", roles.findByName("USER"));
        admin = account("admin-test@example.test", roles.findByName("SUPER_ADMIN"));
        company = new Company(); company.setName("Test Bakery"); company = companies.save(company);
        job = new Job(); job.setName("Baker"); job.setActive(true); job.setCompany(company);
        job.setStartDate(Instant.now().minusSeconds(600)); job.setEndDate(Instant.now().plusSeconds(600));
        job = jobs.save(job);
    }

    User account(String email, Role role) {
        User user = new User(); user.setEmail(email); user.setName("Test"); user.setPassword("hashed-test-password"); user.setRole(role);
        return users.save(user);
    }

    @Test
    void candidateCannotPromoteSelfOrListEmployeeCredentials() throws Exception {
        mvc.perform(put("/api/v1/users").with(jwt().jwt(j -> j.subject(candidate.getEmail())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("id", candidate.getId(), "role", Map.of("id", admin.getRole().getId())))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.statusCode").value(403));
        mvc.perform(get("/api/v1/shifts/employees").with(jwt().jwt(j -> j.subject(candidate.getEmail()))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").with(jwt().jwt(j -> j.subject(candidate.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void profileUpdateCannotChangeOwnerRoleOrCompany() throws Exception {
        mvc.perform(put("/api/v1/users/me").with(jwt().jwt(j -> j.subject(candidate.getEmail())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("id", admin.getId(), "name", "Updated", "age", 26,
                        "gender", "OTHER", "address", "New address", "email", admin.getEmail(),
                        "role", Map.of("id", admin.getRole().getId()), "company", Map.of("id", company.getId())))))
                .andExpect(status().isOk());
        User saved = users.findById(candidate.getId()).orElseThrow();
        assertEquals("Updated", saved.getName());
        assertEquals(26, saved.getAge());
        assertEquals("USER", saved.getRole().getName());
        assertEquals("candidate@example.test", saved.getEmail());
        assertNull(saved.getCompany());
    }

    @Test
    void registrationIgnoresPrivilegedRoleAndExistingId() throws Exception {
        when(emails.checkEmailExist("new@example.test")).thenReturn(true);
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("id", admin.getId(), "email", "new@example.test", "password", "password123",
                        "name", "New", "role", Map.of("id", admin.getRole().getId()), "company", Map.of("id", company.getId())))))
                .andExpect(status().isCreated());
        User created = users.findByEmail("new@example.test");
        assertNotEquals(admin.getId(), created.getId());
        assertEquals("USER", created.getRole().getName());
        assertNull(created.getCompany());
    }

    @Test
    void submissionUsesAuthenticatedApplicantAndPendingStatus() throws Exception {
        mvc.perform(post("/api/v1/resumes").with(jwt().jwt(j -> j.subject(candidate.getEmail())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", admin.getEmail(), "url", "https://storage.example.test/cv.pdf",
                        "status", "APPROVED", "user", Map.of("id", admin.getId()), "job", Map.of("id", job.getId())))))
                .andExpect(status().isCreated());
        var saved = resumes.findByEmailAndJob(candidate.getEmail(), job);
        assertEquals(1, saved.size());
        assertEquals(candidate.getId(), saved.get(0).getUser().getId());
        assertEquals("PENDING", saved.get(0).getStatus().name());
    }

    @Test
    void adminCanUpdateDescriptionAndEmployeesResponseIsMinimal() throws Exception {
        mvc.perform(put("/api/v1/jobs").with(jwt().jwt(j -> j.subject(admin.getEmail())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("id", job.getId(), "name", "Baker", "description", "New description"))))
                .andExpect(status().isOk());
        assertEquals("New description", jobs.findById(job.getId()).orElseThrow().getDescription());
        candidate.setCompany(company); candidate.setRefreshToken("private-token"); candidate.setOtp("123456"); users.save(candidate);
        mvc.perform(get("/api/v1/shifts/employees").param("companyId", company.getId().toString())
                .with(jwt().jwt(j -> j.subject(admin.getEmail()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].email").value(candidate.getEmail()))
                .andExpect(jsonPath("$.data[0].password").doesNotExist())
                .andExpect(jsonPath("$.data[0].refreshToken").doesNotExist())
                .andExpect(jsonPath("$.data[0].otp").doesNotExist());
    }

    @Test
    void tokensCannotBeUsedInterchangeablyAndRefreshIsUnique() {
        var dto = new vn.hoidanit.jobhunter.domain.response.ResLoginDTO();
        dto.setUser(new vn.hoidanit.jobhunter.domain.response.ResLoginDTO.UserLogin(
                candidate.getId(), candidate.getEmail(), candidate.getName(), candidate.getRole()));
        String access = tokens.createAccessToken(candidate.getEmail(), dto);
        String refresh = tokens.createRefreshToken(candidate.getEmail(), dto);
        assertDoesNotThrow(() -> decoder.decode(access));
        assertDoesNotThrow(() -> tokens.checkValidRefreshToken(refresh));
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class, () -> decoder.decode(refresh));
        assertThrows(org.springframework.security.oauth2.jwt.JwtException.class, () -> tokens.checkValidRefreshToken(access));
        assertNotEquals(refresh, tokens.createRefreshToken(candidate.getEmail(), dto));
    }

    @Test
    void hrCannotReadAnotherCompanyOrAllCompaniesWithoutAssignment() throws Exception {
        Role hrRole = new Role(); hrRole.setName("TEST_HR"); hrRole.setActive(true);
        hrRole.setPermissions(permissions.findAll()); hrRole = roles.save(hrRole);
        User hr = account("hr@example.test", hrRole);
        Company otherCompany = new Company(); otherCompany.setName("Other bakery"); otherCompany = companies.save(otherCompany);
        hr.setCompany(otherCompany); users.save(hr);
        Resume resume = new Resume(); resume.setUser(candidate); resume.setJob(job); resume.setEmail(candidate.getEmail());
        resume.setUrl("cv.pdf"); resume = resumes.save(resume);
        mvc.perform(get("/api/v1/resumes/" + resume.getId()).with(jwt().jwt(j -> j.subject(hr.getEmail()))))
                .andExpect(status().isForbidden());
        hr.setCompany(null); users.save(hr);
        mvc.perform(get("/api/v1/resumes").with(jwt().jwt(j -> j.subject(hr.getEmail()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void corsAllowsPatchFromFrontendAndRejectsUnknownOrigins() throws Exception {
        mvc.perform(options("/api/v1/jobs/1/active").header("Origin", "https://jobhunter-fe-one.vercel.app")
                .header("Access-Control-Request-Method", "PATCH").header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/v1/jobs/1/active").header("Origin", "https://untrusted.example.test")
                .header("Access-Control-Request-Method", "PATCH")).andExpect(status().isForbidden());
    }
}
