package cl.casesim.backend.professor;

import cl.casesim.backend.auth.UserRepository;
import cl.casesim.backend.professor.dto.ProfessorStudentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProfessorStudentsService {

    private final UserRepository userRepository;

    public ProfessorStudentsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<ProfessorStudentResponse> getAllStudents() {
        return userRepository.findAllStudentsOrderByNombreAsc()
                .stream()
                .map(student -> new ProfessorStudentResponse(
                        student.getId(),
                        student.getNombre(),
                        student.getEmail(),
                        student.isActivo()
                ))
                .toList();
    }
}
