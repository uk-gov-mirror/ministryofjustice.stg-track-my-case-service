package uk.gov.moj.cp.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.moj.cp.config.ApiPaths;

@RequiredArgsConstructor
@RestController
public class HealthController {

    @GetMapping(ApiPaths.PATH_API_HEALTH)
    public ResponseEntity<String> getHealth() {
        return ResponseEntity.ok("UP");
    }
}
