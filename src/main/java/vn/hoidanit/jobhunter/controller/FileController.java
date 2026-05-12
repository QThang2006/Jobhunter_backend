package vn.hoidanit.jobhunter.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.InputStreamSource;
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
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class FileController {

    @Value("${thang.upload-file.base-uri}")
    private String baseURI;

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/files")
    public ResponseEntity<ResUploadFileDTO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("folder") String folder
    ) throws URISyntaxException, IOException, StorageException {

        if(file == null || file.isEmpty()){
            throw new StorageException("File is empty. Please upload file.");
        }

        String fileName = file.getOriginalFilename();
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();

        List<String> allowed = Arrays.asList("pdf", "jpg", "jpeg", "png", "doc", "docx");
        boolean isValid = allowed.contains(extension);
        if(!isValid){
            throw new StorageException("Invalid file Exception. only allows " + allowed.toString());
        }

        //create a directory if not exist
        fileService.createDirectory(baseURI + folder);

        //store file
        String uploadNameFile = fileService.store(file,folder);

        ResUploadFileDTO res = new ResUploadFileDTO(uploadNameFile, Instant.now());

        return ResponseEntity.ok().body(res);

    }

    @GetMapping("/files")
    public ResponseEntity<Resource> download(
            @RequestParam(value = "fileName", required = false) String fileName,
            @RequestParam(value = "folder",required = false) String folder
    ) throws URISyntaxException, IOException, StorageException {

        if(fileName == null || folder == null){
            throw new StorageException("Missing params : file name or folder not null");
        }

        long fileLength = fileService.getFileLength(fileName,folder);
        if(fileLength == 0){
            throw new StorageException("file with name : " + fileName + "not found");
        }

        //download a file

        InputStreamResource resource = fileService.getResource(fileName,folder);


        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"" + fileName + "\"")
                .contentLength(fileLength)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
