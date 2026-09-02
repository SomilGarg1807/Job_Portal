package com.somil.jobportal.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.somil.jobportal.entity.Users;
import com.somil.jobportal.repository.UsersRepository;
import com.somil.jobportal.util.CustomUserDetails;

@Service
public class CustomUserDetailsService implements UserDetailsService{

	private final UsersRepository usersRepository;
	
	
	@Autowired
	public CustomUserDetailsService(UsersRepository usersRepository) {
		super();
		this.usersRepository = usersRepository;
	}

	//tell spring security how to retrieve a user from the database
	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException{
		Users user=usersRepository.findByEmail(username).orElseThrow(()-> new UsernameNotFoundException("Could not found user"));
	return new CustomUserDetails(user);
	}
}
