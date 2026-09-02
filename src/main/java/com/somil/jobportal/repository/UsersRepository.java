package com.somil.jobportal.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.somil.jobportal.entity.Users;

public interface UsersRepository extends JpaRepository<Users, Integer>{

	/* 
	 * findByEmail: This is a method name following the naming convention used by Spring Data JPA.
	 * It's a wrapper class in Java 8+ that represents a value that may or may not be present.
Using Optional helps to avoid NullPointerException because the caller is forced to handle the "empty" case explicitly.
If the email exists in the database, the Optional will contain the corresponding Users entity; otherwise, it will be empty.*/
	Optional<Users> findByEmail(String email);
}
