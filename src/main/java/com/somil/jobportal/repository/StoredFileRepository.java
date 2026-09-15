package com.somil.jobportal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.somil.jobportal.entity.StoredFile;

public interface StoredFileRepository extends JpaRepository<StoredFile, Integer> {

    Optional<StoredFile> findByOwnerIdAndKind(Integer ownerId, String kind);
}
