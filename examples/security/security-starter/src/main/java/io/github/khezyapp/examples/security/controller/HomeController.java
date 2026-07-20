package io.github.khezyapp.examples.security.controller;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class HomeController {

    @GetMapping(value = "/", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Map<String, String>> home(@AuthenticationPrincipal final UserDetails user) {
        return ResponseEntity.ok(Map.of(
                "message", "Hello, " + user.getUsername() + "!",
                "authorities", String.valueOf(user.getAuthorities())
        ));
    }
}
