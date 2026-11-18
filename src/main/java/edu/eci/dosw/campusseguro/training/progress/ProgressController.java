package edu.eci.dosw.campusseguro.training.progress;

import edu.eci.dosw.campusseguro.training.security.CurrentUser;
import edu.eci.dosw.campusseguro.training.security.Permissions;
import edu.eci.dosw.campusseguro.training.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// RF-13. Only the progress of the user in X-User-Id: there is no route to read another user's progress.
@RestController
public class ProgressController {

    private final ProgressService service;

    public ProgressController(ProgressService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/training/progress/me")
    @RequiresPermission(Permissions.QUIZ_TAKE)
    public ProgressResponse mine() {
        return service.of(CurrentUser.id());
    }
}
