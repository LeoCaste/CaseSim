package cl.casesim.backend.professor;

import cl.casesim.backend.professor.dto.ProfessorStudentResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/professor")
public class ProfessorStudentsController {

    private final ProfessorStudentsService professorStudentsService;

    public ProfessorStudentsController(ProfessorStudentsService professorStudentsService) {
        this.professorStudentsService = professorStudentsService;
    }

    @GetMapping("/students")
    public List<ProfessorStudentResponse> getStudents() {
        return professorStudentsService.getAllStudents();
    }
}
