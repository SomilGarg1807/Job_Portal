package com.somil.jobportal.controller;
import com.somil.jobportal.services.CandidateAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
@Controller
public class CandidateFileController {
    private final CandidateAccessService access;
    public CandidateFileController(CandidateAccessService access) { this.access = access; }
    @GetMapping("/photos/candidate/{id}/{filename:.+}")
    public ResponseEntity<FileSystemResource> candidateFile(@PathVariable int id, @PathVariable String filename) {
        var profile = access.requireAccess(id);
        if (!Objects.equals(filename, profile.getResume()) && !Objects.equals(filename, profile.getProfilePhoto()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var file = access.file(profile, filename);
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        if (Objects.equals(filename, profile.getProfilePhoto())) {
            String lower = filename.toLowerCase(java.util.Locale.ROOT);
            if (lower.endsWith(".png")) type = MediaType.IMAGE_PNG;
            else if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) type = MediaType.IMAGE_JPEG;
            else if (lower.endsWith(".webp")) type = MediaType.parseMediaType("image/webp");
        }
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(type).body(new FileSystemResource(file));
    }
}
