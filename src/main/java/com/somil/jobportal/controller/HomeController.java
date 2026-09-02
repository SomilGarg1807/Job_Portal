package com.somil.jobportal.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

//The @Controller annotation in Spring MVC (Model-View-Controller) is used to mark a class as a controller, which is a specialized 
//component in a Spring application. Essentially, it serves as a hub where incoming requests are handled and mapped to the appropriate
//handler methods
@Controller 
public class HomeController {

	@GetMapping("/")
	public String home() {
		return "index";
	}
}
