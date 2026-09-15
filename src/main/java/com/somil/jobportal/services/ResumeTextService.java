package com.somil.jobportal.services;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.nio.file.*;
import java.io.IOException;
@Service
public class ResumeTextService {
    public String extract(Path file) throws IOException {
        if (Files.size(file) > 3 * 1024 * 1024) throw new IOException("PDF exceeds 3 MB");
        byte[] bytes;
        try (var stream = Files.newInputStream(file)) { bytes = stream.readNBytes(3 * 1024 * 1024 + 1); }
        return extract(bytes);
    }
    public String extract(byte[] bytes) throws IOException {
        if (bytes.length > 3 * 1024 * 1024) throw new IOException("PDF exceeds 3 MB");
        try (var document = Loader.loadPDF(bytes)) {
            if (document.getNumberOfPages() > 10 || document.isEncrypted()) throw new IOException("Unsupported PDF");
            var stripper = new PDFTextStripper();
            String text = stripper.getText(document).trim();
            if (text.length() > 12000) throw new IOException("Resume exceeds text limit");
            return text;
        }
    }
}
