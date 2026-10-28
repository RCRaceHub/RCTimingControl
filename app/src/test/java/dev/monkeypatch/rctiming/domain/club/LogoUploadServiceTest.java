package dev.monkeypatch.rctiming.domain.club;

import dev.monkeypatch.rctiming.domain.audit.AuditService;
import dev.monkeypatch.rctiming.infrastructure.storage.ObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class LogoUploadServiceTest {

    private final LogoUploadService service = new LogoUploadService(
            mock(ObjectStorageService.class), mock(ClubProfileRepository.class), mock(AuditService.class));

    @Test
    void refusesSvgBecauseItCanCarryAScript() {
        MockMultipartFile svg = new MockMultipartFile("file", "logo.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.uploadLogo(null, 1L, svg));
    }
}
