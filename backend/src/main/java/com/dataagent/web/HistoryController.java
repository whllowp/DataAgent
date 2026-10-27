package com.dataagent.web;

import com.dataagent.security.ApiTokenFilter;
import com.dataagent.service.HistoryService;
import com.dataagent.web.dto.HistoryDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api")
public class HistoryController {

    private final HistoryService history;

    public HistoryController(HistoryService history) {
        this.history = history;
    }

    @GetMapping("/history")
    public HistoryDtos.HistoryPage search(@RequestParam(required = false) UUID datasourceId,
                                          @RequestParam(required = false) Boolean solved,
                                          @RequestParam(required = false, name = "q") String query,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return history.search(datasourceId, solved, query, page, size);
    }

    @GetMapping("/history/{id}")
    public HistoryDtos.HistoryDetail detail(@PathVariable UUID id) {
        return history.detail(id);
    }

    @PatchMapping("/history/{id}/solve")
    public HistoryDtos.SolveResult solve(@PathVariable UUID id,
                                         @Valid @RequestBody HistoryDtos.SolveRequest request,
                                         @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        return history.markSolved(id, request, user);
    }

    @PatchMapping("/history/{id}/note")
    public HistoryDtos.HistoryView note(@PathVariable UUID id,
                                        @Valid @RequestBody HistoryDtos.NoteRequest request) {
        return history.updateNote(id, request);
    }

    @GetMapping("/stats/overview")
    public HistoryDtos.QueryStats overview() {
        return history.stats();
    }
}
