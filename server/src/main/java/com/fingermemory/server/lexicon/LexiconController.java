package com.fingermemory.server.lexicon;

import com.fingermemory.server.auth.CurrentUser;
import com.fingermemory.server.auth.FmUserPrincipal;
import com.fingermemory.server.lexicon.LexiconDtos.ArchiveLexiconRequest;
import com.fingermemory.server.lexicon.LexiconDtos.CreateLexiconRequest;
import com.fingermemory.server.lexicon.LexiconDtos.LexiconView;
import com.fingermemory.server.lexicon.LexiconDtos.UpdateLexiconRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/lexicons")
public class LexiconController {
    private final LexiconService lexiconService;

    public LexiconController(LexiconService lexiconService) {
        this.lexiconService = lexiconService;
    }

    @GetMapping
    public List<LexiconView> list(Authentication authentication,
                                  @RequestParam(defaultValue = "false") boolean includeArchived,
                                  @RequestParam(defaultValue = "50") int limit) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return lexiconService.list(principal.id(), includeArchived, limit).stream()
                .map(LexiconView::from)
                .toList();
    }

    @GetMapping("/{id}")
    public LexiconView get(Authentication authentication, @PathVariable String id) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return LexiconView.from(lexiconService.get(principal.id(), id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LexiconView create(Authentication authentication,
                              @Valid @RequestBody CreateLexiconRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return LexiconView.from(lexiconService.create(principal.id(), request));
    }

    @PutMapping("/{id}")
    public LexiconView update(Authentication authentication, @PathVariable String id,
                              @Valid @RequestBody UpdateLexiconRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return LexiconView.from(lexiconService.update(principal.id(), id, request));
    }

    @PostMapping("/{id}/archive")
    public LexiconView archive(Authentication authentication, @PathVariable String id,
                               @Valid @RequestBody ArchiveLexiconRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return LexiconView.from(lexiconService.setArchived(principal.id(), id, true, request.version()));
    }

    @PostMapping("/{id}/restore")
    public LexiconView restore(Authentication authentication, @PathVariable String id,
                               @Valid @RequestBody ArchiveLexiconRequest request) {
        FmUserPrincipal principal = CurrentUser.require(authentication);
        return LexiconView.from(lexiconService.setArchived(principal.id(), id, false, request.version()));
    }
}
