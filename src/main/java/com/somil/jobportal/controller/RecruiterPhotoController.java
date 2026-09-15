package com.somil.jobportal.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import com.somil.jobportal.services.FileStorageService;

@Controller
public class RecruiterPhotoController {

    private final FileStorageService storage;

    public RecruiterPhotoController(FileStorageService storage) {
        this.storage = storage;
    }

    // Photo names change on every upload, so the browser can keep them for a week.
    @GetMapping("/photos/recruiter/{id}/{filename:.+}")
    public ResponseEntity<byte[]> photo(@PathVariable int id, @PathVariable String filename) {
        var file = storage.find(FileStorageService.RECRUITER_PHOTO, id, filename)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                .contentType(MediaType.parseMediaType(file.getContentType())).body(file.getData());
    }
}
