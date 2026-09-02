# HotDevJobs

A full-stack job portal where job seekers can discover and apply for roles, while recruiters can publish openings and manage candidates.

## Live application

**[Open HotDevJobs](https://job-portal-0lgp.onrender.com/)**

The application is hosted on Render's free tier. Its first request after a period of inactivity can take a short time while the service starts.

## Features

- Separate job seeker and recruiter account types
- Secure registration and login with Spring Security
- Search jobs by title, location, employment type, workplace type, and posting date
- Job seeker profiles, applications, and saved jobs
- Recruiter profiles, job posting, editing, and candidate tracking
- Responsive Thymeleaf interface
- Automatic creation and update timestamps on persisted records

## Technology

- Java 17
- Spring Boot 3, Spring MVC, Spring Security, and Spring Data JPA
- Hibernate
- Thymeleaf, Bootstrap, HTML, CSS, and JavaScript
- TiDB Cloud (MySQL-compatible)
- Maven
- Docker and Render

## Run locally

Prerequisites: Java 17+, Maven, and a MySQL-compatible database.

Set these environment variables before starting the application:

```text
DB_URL=jdbc:mysql://your-host:4000/jobportal?sslMode=VERIFY_IDENTITY
DB_USERNAME=your-username
DB_PASSWORD=your-password
```

Then run:

```bash
mvn spring-boot:run
```

Open `http://localhost:8080`.

Never commit database credentials. For deployment, configure these values as secret environment variables in Render.

## Deployment

The included `Dockerfile` and `render.yaml` make the project ready for Render. Render supplies the public `PORT`; the application binds to it automatically.

Database schema updates are managed by Hibernate through `spring.jpa.hibernate.ddl-auto=update`.
