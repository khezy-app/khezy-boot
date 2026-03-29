package io.github.khezyapp.api.audit.model;

import lombok.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Getter
@Setter
@Builder
@NoArgsConstructor
@ToString
public class AuditMetadata {
    private String httpMethod;
    private String requestUri;
    private String queryString;
    private String userAgent;
    private String ip;
    @Singular
    private Map<String, Object> properties;

    public AuditMetadata(final String httpMethod,
                         final String requestUri,
                         final String queryString,
                         final String userAgent,
                         final String ip,
                         final Map<String, Object> properties) {
        this.httpMethod = httpMethod;
        this.requestUri = requestUri;
        this.queryString = queryString;
        this.userAgent = userAgent;
        this.ip = ip;
        if (Objects.nonNull(properties)) {
            this.properties = new LinkedHashMap<>(properties);
        }
    }

    public void setProperty(final String name,
                            final Object value) {
        if (Objects.isNull(properties)) {
            this.properties = new LinkedHashMap<>();
        }
        this.properties.put(name, value);
    }
}
