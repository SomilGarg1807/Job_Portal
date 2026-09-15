package com.somil.jobportal.services;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.somil.jobportal.entity.StoredFile;
import com.somil.jobportal.repository.StoredFileRepository;

/**
 * Stores uploads in the database, keeping them small: photos are re-encoded as a JPEG whose
 * longest side is {@value #PHOTO_SIZE}px (typically 10-40 KB), and resumes must be real PDFs of
 * at most 3 MB, the same limit the resume text extraction accepts.
 */
@Service
public class FileStorageService {

    public static final String CANDIDATE_PHOTO = "CANDIDATE_PHOTO";
    public static final String CANDIDATE_RESUME = "CANDIDATE_RESUME";
    public static final String RECRUITER_PHOTO = "RECRUITER_PHOTO";

    static final int PHOTO_SIZE = 320;
    static final int MAX_RESUME_BYTES = 3 * 1024 * 1024;
    private static final long MAX_SOURCE_PIXELS = 50_000_000L;
    private static final String PHOTO_ERROR = "Profile photo must be a PNG or JPEG image.";
    private static final String RESUME_ERROR = "Resume must be a PDF of 3 MB or less.";

    public record Upload(String name, String contentType, byte[] data) {}

    private final StoredFileRepository files;

    public FileStorageService(StoredFileRepository files) {
        this.files = files;
    }

    /** Validates and shrinks a photo. Throws IllegalArgumentException with a user-facing message. */
    public Upload preparePhoto(MultipartFile file) {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.getInputStream())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) throw new IllegalArgumentException(PHOTO_ERROR);
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if ((long) width * height > MAX_SOURCE_PIXELS) throw new IllegalArgumentException(PHOTO_ERROR);
                // Decode at reduced resolution so a large photo never needs full-size memory.
                ImageReadParam param = reader.getDefaultReadParam();
                int step = Math.max(1, Math.max(width, height) / (PHOTO_SIZE * 2));
                param.setSourceSubsampling(step, step, 0, 0);
                byte[] jpeg = toJpeg(shrink(reader.read(0, param)));
                // A new name per upload lets browsers cache photos without showing a stale one.
                return new Upload("photo-" + System.currentTimeMillis() + ".jpg", "image/jpeg", jpeg);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            if (ex instanceof IllegalArgumentException invalid) throw invalid;
            throw new IllegalArgumentException(PHOTO_ERROR, ex);
        }
    }

    /** Validates a resume PDF. Throws IllegalArgumentException with a user-facing message. */
    public Upload prepareResume(MultipartFile file) {
        if (file.getSize() > MAX_RESUME_BYTES) throw new IllegalArgumentException(RESUME_ERROR);
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException(RESUME_ERROR, ex);
        }
        if (data.length < 5 || !"%PDF-".equals(new String(data, 0, 5, StandardCharsets.US_ASCII)))
            throw new IllegalArgumentException(RESUME_ERROR);
        return new Upload(resumeName(file.getOriginalFilename()), "application/pdf", data);
    }

    @Transactional
    public void store(String kind, int ownerId, Upload upload) {
        StoredFile file = files.findByOwnerIdAndKind(ownerId, kind).orElseGet(StoredFile::new);
        file.setOwnerId(ownerId);
        file.setKind(kind);
        file.setFileName(upload.name());
        file.setContentType(upload.contentType());
        file.setData(upload.data());
        files.save(file);
    }

    /** Returns the owner's file of this kind, only if it is still the one named on their profile. */
    public Optional<StoredFile> find(String kind, int ownerId, String fileName) {
        if (!StringUtils.hasText(fileName)) return Optional.empty();
        return files.findByOwnerIdAndKind(ownerId, kind).filter(file -> fileName.equals(file.getFileName()));
    }

    private static BufferedImage shrink(BufferedImage source) {
        double ratio = Math.min(1.0, (double) PHOTO_SIZE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * ratio));
        int height = Math.max(1, (int) Math.round(source.getHeight() * ratio));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(Color.WHITE); // transparent PNG areas become white instead of black
            g.fillRect(0, 0, width, height);
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    private static byte[] toJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.8f);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static String resumeName(String original) {
        String name = StringUtils.getFilename(StringUtils.cleanPath(original == null ? "" : original));
        if (name != null) name = name.substring(name.lastIndexOf('\\') + 1).trim();
        if (!StringUtils.hasText(name)) name = "resume.pdf";
        if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) name += ".pdf";
        return name.length() > 120 ? name.substring(name.length() - 120) : name;
    }
}
