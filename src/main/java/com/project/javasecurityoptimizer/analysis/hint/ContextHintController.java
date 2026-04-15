package com.project.javasecurityoptimizer.analysis.hint;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/analysis/hints")
public class ContextHintController {
    private final ContextAwareJitHintService contextAwareJitHintService;

    public ContextHintController(ContextAwareJitHintService contextAwareJitHintService) {
        this.contextAwareJitHintService = contextAwareJitHintService;
    }

    @PostMapping("/jit-context")
    public ContextHintResponse analyze(@RequestBody ContextHintRequest request) {
        try {
            return contextAwareJitHintService.analyze(request);
        } catch (IllegalArgumentException illegalArgumentException) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, illegalArgumentException.getMessage(), illegalArgumentException);
        }
    }
}
