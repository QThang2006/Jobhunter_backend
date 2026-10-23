package vn.hoidanit.jobhunter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import vn.hoidanit.jobhunter.domain.*;
import vn.hoidanit.jobhunter.repository.*;
import vn.hoidanit.jobhunter.util.constant.*;
import vn.hoidanit.jobhunter.util.error.IdInvalidException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegressionTests {
    @Test
    void userSerializationNeverExposesCredentialsButAcceptsPasswordInput() throws Exception {
        ObjectMapper json = new ObjectMapper();
        User user = json.readValue("{\"email\":\"user@example.test\",\"password\":\"input-password\"}", User.class);
        assertEquals("input-password", user.getPassword());
        user.setRefreshToken("private-token");
        user.setOtp("123456");
        String output = json.writeValueAsString(user);
        assertFalse(output.contains("password"));
        assertFalse(output.contains("refreshToken"));
        assertFalse(output.contains("otp"));
    }

    @Test
    void otpExpiresImmediatelyEvenBeforeCleanupRuns() {
        UserRepository users = mock(UserRepository.class);
        UserService service = new UserService(users, null, null, null, null, null, null);
        User user = new User(); user.setOtp("123456");
        when(users.findByEmail("user@example.test")).thenReturn(user);
        user.setOtpExpiredAt(Instant.now().minusSeconds(1));
        assertFalse(service.verifyOTP("user@example.test", "123456"));
        user.setOtpExpiredAt(Instant.now().plusSeconds(30));
        assertTrue(service.verifyOTP("user@example.test", "123456"));
        assertFalse(service.verifyOTP("user@example.test", "654321"));
        user.setOtpExpiredAt(null);
        assertFalse(service.verifyOTP("user@example.test", "123456"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://127.0.0.1/admin", "file:///etc/passwd", "https://evil.test/storage/v1/object/public/resume/cv.pdf",
        "https://storage.example.test.evil.test/storage/v1/object/public/resume/cv.pdf",
        "https://evil.test@storage.example.test/storage/v1/object/public/resume/cv.pdf",
        "https://storage.example.test:444/storage/v1/object/public/resume/cv.pdf",
        "https://storage.example.test/storage/v1/object/public/resume/../company/logo.png",
        "https://storage.example.test/storage/v1/object/public/resume/%2e%2e%2fsecret",
        "https://storage.example.test/storage/v1/object/public/resume/cv.pdf?redirect=https://evil.test",
        "https://storage.example.test/storage/v1/object/public/private/file.pdf"
    })
    void rejectsDownloadsOutsideExpectedStorage(String url) {
        FileService files = new FileService();
        ReflectionTestUtils.setField(files, "supabaseUrl", "https://storage.example.test");
        assertThrows(IllegalArgumentException.class, () -> files.validateDownloadUrl(url));
    }

    @Test
    void allowsExistingPublicStorageUrls() {
        FileService files = new FileService();
        ReflectionTestUtils.setField(files, "supabaseUrl", "https://storage.example.test");
        String url = "https://storage.example.test/storage/v1/object/public/resume/123-cv.pdf";
        assertEquals(url, files.validateDownloadUrl(url).toString());
    }

    @Test
    void rejectsClosedAndDuplicateApplications() throws Exception {
        ResumeRepository resumes = mock(ResumeRepository.class);
        ResumeService service = new ResumeService(null, resumes, null, null, null, null, null, null);
        Job job = new Job(); job.setActive(true);
        job.setStartDate(Instant.now().minusSeconds(60)); job.setEndDate(Instant.now().plusSeconds(60));
        when(resumes.findByEmailAndJob("user@example.test", job)).thenReturn(List.of());
        assertDoesNotThrow(() -> service.validateApplication(job, "user@example.test"));
        Resume existing = new Resume(); existing.setCreatedAt(job.getStartDate());
        when(resumes.findByEmailAndJob("user@example.test", job)).thenReturn(List.of(existing));
        assertThrows(IdInvalidException.class, () -> service.validateApplication(job, "user@example.test"));
        job.setActive(false);
        assertThrows(IdInvalidException.class, () -> service.validateApplication(job, "user@example.test"));
        job.setActive(true); job.setEndDate(Instant.now().minusSeconds(1));
        assertThrows(IdInvalidException.class, () -> service.validateApplication(job, "user@example.test"));
    }

    @Test
    void shiftValidationChecksCompanyAndOvernightOverlap() throws Exception {
        WorkShiftRepository shifts = mock(WorkShiftRepository.class);
        UserRepository users = mock(UserRepository.class);
        WorkShiftService service = new WorkShiftService(shifts, users, null, null, null, null);
        ReflectionTestUtils.setField(service, "access", mock(AccessService.class));
        Company company = new Company(); company.setId(1L);
        User employee = new User(); employee.setId(2L); employee.setCompany(company);
        when(users.lockById(2L)).thenReturn(Optional.of(employee));
        WorkShift proposed = shift(4L, employee, company, LocalDate.of(2026, 10, 22), "01:00", "06:00");
        WorkShift overnight = shift(3L, employee, company, proposed.getShiftDate().minusDays(1), "22:00", "02:00");
        when(shifts.findByUserAndShiftDateBetweenOrderByShiftDateAscStartTimeAsc(any(), any(), any())).thenReturn(List.of(overnight));
        assertThrows(IdInvalidException.class, () -> service.validateShift(proposed));
        proposed.setStartTime("02:00");
        assertDoesNotThrow(() -> service.validateShift(proposed));
        proposed.setEndTime("02:00");
        assertThrows(IdInvalidException.class, () -> service.validateShift(proposed));
        proposed.setEndTime("06:00");
        employee.setCompany(null);
        assertThrows(IdInvalidException.class, () -> service.validateShift(proposed));
    }

    private WorkShift shift(Long id, User user, Company company, LocalDate date, String start, String end) {
        WorkShift shift = new WorkShift(); shift.setId(id); shift.setUser(user); shift.setCompany(company);
        shift.setShiftDate(date); shift.setStartTime(start); shift.setEndTime(end); shift.setStatus(WorkShiftStatusEnum.ASSIGNED);
        return shift;
    }
}
