package io.kestra.plugin.cobol.models;

import io.kestra.core.models.annotations.PluginProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import jakarta.validation.constraints.NotNull;

/**
 * COBOL source configuration - specify either inline source or a URI.
 */
@Builder
@Getter
public class SourceConfig {

    @Schema(
        title = "Inline COBOL source code",
        description = "Multiline string containing COBOL source"
    )
    @PluginProperty(dynamic = true)
    private String inline;

    @Schema(
        title = "URI to COBOL source file",
        description = "HTTP(S) URL or Kestra internal storage URI (kestra://...)"
    )
    @PluginProperty(dynamic = true)
    private String uri;

    /**
     * Validate that exactly one source method is specified.
     */
    public void validate() {
        if (inline == null && uri == null) {
            throw new IllegalArgumentException("Must specify either 'inline' or 'uri' for source");
        }
        if (inline != null && uri != null) {
            throw new IllegalArgumentException("Cannot specify both 'inline' and 'uri' for source");
        }
    }

    /**
     * Check if inline source is provided.
     */
    public boolean isInline() {
        return inline != null;
    }

    /**
     * Get the source content (inline or from URI).
     */
    public String getContent() {
        return inline != null ? inline : null;
    }
}