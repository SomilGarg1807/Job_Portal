package com.somil.jobportal.controller;
import com.somil.jobportal.services.CandidateAccessService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
@Controller
public class CandidateFileController {
    private final CandidateAccessService access;
    public CandidateFileController(CandidateAccessService access) { this.access = access; }
    @GetMapping("/photos/candidate/{id}/{filename:.+}")
    public ResponseEntity<byte[]> candidateFile(@PathVariable int id, @PathVariable String filename) {
        var profile = access.requireAccess(id);
        var file = access.file(profile, filename);
        MediaType type = file.getContentType().startsWith("image/")
                ? MediaType.parseMediaType(file.getContentType()) : MediaType.APPLICATION_OCTET_STREAM;
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(type).body(file.getData());
    }
}
