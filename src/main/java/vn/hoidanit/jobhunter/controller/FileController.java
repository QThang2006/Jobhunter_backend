package vn.hoidanit.jobhunter.controller;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.hoidanit.jobhunter.domain.response.file.ResUploadFileDTO;
import vn.hoidanit.jobhunter.service.FileService;
import vn.hoidanit.jobhunter.util.error.StorageException;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    private final FileService fileService;

    public FileController(
            FileService fileService
    ) {
        this.fileService = fileService;
    }

    // =========================
    // UPLOAD
    // =========================
    @PostMapping("/files")
    public ResponseEntity<ResUploadFileDTO> upload(
            @RequestParam("file")
            MultipartFile file,

            @RequestParam("folder")
            String folder

    ) throws IOException, StorageException {

        if (file == null || file.isEmpty()) {
            throw new StorageException(
                    "File is empty. Please upload file."
            );
        }

        String fileName =
                file.getOriginalFilename();

        String extension =
                fileName.substring(
                        fileName.lastIndexOf(".") + 1
                ).toLowerCase();

        List<String> allowed =
                Arrays.asList(
                        "pdf",
                        "jpg",
                        "jpeg",
                        "png",
                        "doc",
                        "docx"
                );

        boolean isValid =
                allowed.contains(extension);

        if (!isValid) {
            throw new StorageException(
                    "Invalid file Exception. only allows "
                            + allowed
            );
        }

        // upload to supabase
        String uploadedUrl =
                fileService.store(
                        file,
                        folder
                );

        ResUploadFileDTO res =
                new ResUploadFileDTO(
                        uploadedUrl,
                        Instant.now()
                );

        return ResponseEntity.ok()
                .body(res);
    }

    // =========================
    // DOWNLOAD
    // =========================
    @GetMapping("/files")
    public ResponseEntity<Resource> download(
            @RequestParam("fileUrl")
            String fileUrl
    ) throws IOException {

        long fileLength =
                fileService.getFileLength(
                        fileUrl
                );

        InputStreamResource resource =
                fileService.getResource(
                        fileUrl
                );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment"
                )
                .contentLength(fileLength)
                .contentType(
                        MediaType.APPLICATION_OCTET_STREAM
                )
                .body(resource);
    }
}