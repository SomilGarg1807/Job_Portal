package com.somil.jobportal.entity;

import jakarta.persistence.*;

/**
 * An uploaded profile photo or resume, kept in the database so it survives redeploys
 * (Render's disk is wiped on every deploy). One row per owner and kind: re-uploading
 * replaces the old file instead of adding another.
 */
@Entity
@Table(name = "stored_file", uniqueConstraints = @UniqueConstraint(columnNames = {"owner_id", "kind"}))
public class StoredFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "owner_id", nullable = false)
    private Integer ownerId;

    @Column(nullable = false, length = 20)
    private String kind;

    @Column(nullable = false, length = 160)
    private String fileName;

    @Column(nullable = false, length = 40)
    private String contentType;

    private int size;

    @Lob
    @Column(nullable = false, columnDefinition = "MEDIUMBLOB")
    private byte[] data;

    public Integer getId() {
        return id;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer ownerId) {
        this.ownerId = ownerId;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public int getSize() {
        return size;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
        this.size = data == null ? 0 : data.length;
    }
}
