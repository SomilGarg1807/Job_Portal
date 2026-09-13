package com.somil.jobportal.repository;
import com.somil.jobportal.entity.ApplicationStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ApplicationStatusEventRepository extends JpaRepository<ApplicationStatusEvent, Integer> {
    List<ApplicationStatusEvent> findByApplicationIdOrderByOccurredAtAscIdAsc(Integer id);
}
