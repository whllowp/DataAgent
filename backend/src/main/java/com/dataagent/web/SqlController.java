package com.dataagent.web;

import com.dataagent.security.ApiTokenFilter;
import com.dataagent.service.JdbcQueryService;
import com.dataagent.web.dto.SqlDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql")
public class SqlController {

    private final JdbcQueryService queries;

    public SqlController(JdbcQueryService queries) {
        this.queries = queries;
    }

    @PostMapping("/execute")
    public SqlDtos.QueryResult execute(@Valid @RequestBody SqlDtos.ExecuteRequest request,
                                       @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        return queries.execute(request.datasourceId(), request.sql(), request.maxRows(),
                request.timeoutSeconds(), Boolean.TRUE.equals(request.includePlan()),
                user, request.problem(), request.tags());
    }

    @PostMapping("/explain")
    public SqlDtos.ExplainResult explain(@Valid @RequestBody SqlDtos.ExplainRequest request,
                                         @RequestAttribute(name = ApiTokenFilter.USER_ATTRIBUTE) String user) {
        return queries.explain(request.datasourceId(), request.sql(), user);
    }

    @PostMapping("/validate")
    public SqlDtos.ValidateResult validate(@Valid @RequestBody SqlDtos.ExplainRequest request) {
        return queries.validate(request.sql());
    }
}
