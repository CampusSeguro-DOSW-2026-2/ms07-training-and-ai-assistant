package edu.eci.dosw.campusseguro.training.progress;

import edu.eci.dosw.campusseguro.training.security.CurrentUser;
import edu.eci.dosw.campusseguro.training.security.Permissions;
import edu.eci.dosw.campusseguro.training.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// RF-13. The user always comes from X-User-Id, never from the body. Access rules live in SecurityConfig.
@RestController
public class ContentViewController {

    private final ContentViewService service;

    public ContentViewController(ContentViewService service) {
        this.service = service;
    }

    // 201 the first time the user opens the content; 200 when only the date of the last view changes.
    @PostMapping("/api/v1/training/contents/{id}/views")
    @RequiresPermission(Permissions.CONTENT_READ)
    public ResponseEntity<ContentViewResponse> register(@PathVariable UUID id) {
        ContentViewService.Result result = service.register(CurrentUser.id(), id);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.view());
    }
}
