package com.somil.jobportal.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.somil.jobportal.entity.UsersType;
import com.somil.jobportal.repository.UsersTypeRepository;

@Service
public class UsersTypeService {
	
	private final UsersTypeRepository usersTypeRepository;
	
	public UsersTypeService(UsersTypeRepository usersTypeRepository) {
		this.usersTypeRepository = usersTypeRepository;
	}
	
	public List<UsersType> getAll(){
		return usersTypeRepository.findAll();
	}
}