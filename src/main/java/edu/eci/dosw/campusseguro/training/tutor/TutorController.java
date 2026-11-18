package edu.eci.dosw.campusseguro.training.tutor;

import edu.eci.dosw.campusseguro.training.security.Permissions;
import edu.eci.dosw.campusseguro.training.security.RequiresPermission;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// RF-15. Access rules live in SecurityConfig; @RequiresPermission documents them in the local swagger.
@RestController
@RequestMapping("/api/v1/tutor")
public class TutorController {

    private final TutorService service;

    public TutorController(TutorService service) {
        this.service = service;
    }

    @PostMapping("/questions")
    @RequiresPermission(Permissions.TUTOR_USE)
    public TutorAnswerResponse ask(@RequestBody TutorQuestionRequest request) {
        return service.ask(request.question());
    }
}
