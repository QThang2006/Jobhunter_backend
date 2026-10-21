package vn.hoidanit.jobhunter.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import vn.hoidanit.jobhunter.util.error.StorageException;

import java.io.*;
import java.net.URL;
import java.text.Normalizer;
import java.util.UUID;

@Service
public class FileService {

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.key}")
    private String supabaseKey;

    // =========================
    // UPLOAD FILE
    // =========================
    public String store(
            MultipartFile file,
            String folder
    ) throws IOException, StorageException {

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "document";
        }

        // Tách phần tên gốc và phần mở rộng (extension)
        String extension = "";
        String baseName = originalName;
        int dotIndex = originalName.lastIndexOf(".");
        if (dotIndex > 0) {
            extension = originalName.substring(dotIndex);
            baseName = originalName.substring(0, dotIndex);
        }

        // Chuẩn hóa tên file: loại bỏ dấu tiếng Việt, thay thế khoảng trắng và ký tự lạ bằng dấu gạch ngang
        String cleanBase = Normalizer.normalize(baseName, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .replaceAll("[^a-zA-Z0-9-_]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-+|-+$", "");

        if (cleanBase.isBlank()) {
            cleanBase = "document";
        }

        // Tên file cuối cùng: các ký tự liền nhau, không chứa khoảng cách, đảm bảo chuẩn URL
        String finalName = UUID.randomUUID() + "-" + cleanBase + extension.toLowerCase();

        // upload endpoint
        String uploadUrl =
                supabaseUrl
                        + "/storage/v1/object/"
                        + folder
                        + "/"
                        + finalName;

        HttpHeaders headers =
                new HttpHeaders();

        headers.setBearerAuth(supabaseKey);

        String contentType = file.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        headers.setContentType(MediaType.parseMediaType(contentType));

        HttpEntity<byte[]> entity =
                new HttpEntity<>(
                        file.getBytes(),
                        headers
                );

        RestTemplate restTemplate =
                new RestTemplate();

        ResponseEntity<String> response =
                restTemplate.exchange(
                        uploadUrl,
                        HttpMethod.POST,
                        entity,
                        String.class
                );

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new StorageException(
                    "Upload file failed"
            );
        }

        // public url
        return supabaseUrl
                + "/storage/v1/object/public/"
                + folder
                + "/"
                + finalName;
    }

    // =========================
    // DOWNLOAD FILE
    // =========================
    public InputStreamResource getResource(
            String fileUrl
    ) throws IOException {

        URL url = new URL(fileUrl);

        InputStream inputStream =
                url.openStream();

        return new InputStreamResource(
                inputStream
        );
    }

    // =========================
    // GET FILE LENGTH
    // =========================
    public long getFileLength(
            String fileUrl
    ) throws IOException {

        URL url = new URL(fileUrl);

        return url.openConnection()
                .getContentLengthLong();
    }
}
