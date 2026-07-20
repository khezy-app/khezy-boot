package io.github.khezyapp.examples.security.mfa.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SecureController {

    @GetMapping(value = "/secure", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> secure(
            @AuthenticationPrincipal final UserDetails user
    ) {
        return ResponseEntity.ok(Map.of(
                "message", "Access granted to " + user.getUsername(),
                "authorities", user.getAuthorities().toString()
        ));
    }
}
