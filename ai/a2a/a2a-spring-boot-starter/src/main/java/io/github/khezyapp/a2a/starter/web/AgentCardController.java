package io.github.khezyapp.a2a.starter.web;

import io.github.khezyapp.a2a.core.dispatch.A2ARequestDispatcher;
import io.github.khezyapp.a2a.core.identity.CallerIdentityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.a2aproject.sdk.spec.AgentCard;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** A2A discovery endpoint serving the agent card at {@code /.well-known/agent-card.json}. */
@RestController
public class AgentCardController {

    private final A2ARequestDispatcher dispatcher;
    private final CallerIdentityResolver identityResolver;

    AgentCardController(final A2ARequestDispatcher dispatcher, final CallerIdentityResolver identityResolver) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.identityResolver = Objects.requireNonNull(identityResolver, "identityResolver");
    }

    @GetMapping(path = "/.well-known/agent-card.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AgentCard> agentCard(final HttpServletRequest request) {
        return ResponseEntity.ok(dispatcher.agentCard(HttpCallContext.from(request, identityResolver)));
    }
}
