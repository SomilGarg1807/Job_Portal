package com.somil.jobportal.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;

import com.somil.jobportal.repository.StoredFileRepository;

@DataJpaTest
@Import(FileStorageService.class)
class FileStorageServiceTests {

    @Autowired FileStorageService storage;
    @Autowired StoredFileRepository files;

    @Test
    void largePhotoIsShrunkToSmallJpeg() throws Exception {
        BufferedImage big = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(big, "png", png);

        var upload = storage.preparePhoto(new MockMultipartFile("image", "me.png", "image/png", png.toByteArray()));

        assertThat(upload.contentType()).isEqualTo("image/jpeg");
        assertThat(upload.name()).startsWith("photo-").endsWith(".jpg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(upload.data()));
        assertThat(Math.max(stored.getWidth(), stored.getHeight())).isEqualTo(FileStorageService.PHOTO_SIZE);
        assertThat(upload.data().length).isLessThan(40_000);
    }

    @Test
    void nonImagesAndNonPdfsAreRejectedWithFriendlyMessages() {
        var text = new MockMultipartFile("f", "x.png", "image/png", "not an image".getBytes());
        assertThatThrownBy(() -> storage.preparePhoto(text)).hasMessageContaining("PNG or JPEG");
        assertThatThrownBy(() -> storage.prepareResume(text)).hasMessageContaining("PDF");
        var huge = new MockMultipartFile("f", "cv.pdf", "application/pdf", new byte[FileStorageService.MAX_RESUME_BYTES + 1]);
        assertThatThrownBy(() -> storage.prepareResume(huge)).hasMessageContaining("3 MB");
    }

    @Test
    void reuploadReplacesTheSingleStoredFile() {
        var first = storage.prepareResume(new MockMultipartFile("pdf", "C:\\docs\\Old CV", "application/pdf", "%PDF-1.4 old".getBytes()));
        assertThat(first.name()).isEqualTo("Old CV.pdf");
        storage.store(FileStorageService.CANDIDATE_RESUME, 7, first);
        storage.store(FileStorageService.CANDIDATE_RESUME, 7,
                storage.prepareResume(new MockMultipartFile("pdf", "new.pdf", "application/pdf", "%PDF-1.7 new".getBytes())));

        assertThat(files.count()).isEqualTo(1);
        assertThat(storage.find(FileStorageService.CANDIDATE_RESUME, 7, "Old CV.pdf")).isEmpty();
        assertThat(storage.find(FileStorageService.CANDIDATE_RESUME, 7, "new.pdf")).get()
                .satisfies(file -> assertThat(new String(file.getData())).isEqualTo("%PDF-1.7 new"));
    }
}
