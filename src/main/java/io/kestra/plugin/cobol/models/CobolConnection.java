package io.kestra.plugin.cobol.models;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.runners.RunContext;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import jakarta.validation.constraints.NotNull;

/**
 * Connection configuration for IBM i systems.
 */
@Builder
@Getter
public class CobolConnection {

    @Schema(
        title = "IBM i host name or IP address"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private String host;

    @Schema(
        title = "User profile name"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private String user;

    @Schema(
        title = "Password",
        description = "Should be stored as a Kestra secret"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    private String password;

    @Schema(
        title = "Port number",
        description = "Default is 23 (as-signon)"
    )
    @PluginProperty
    @Builder.Default
    private Integer port = 23;

    @Schema(
        title = "Connection timeout in seconds"
    )
    @PluginProperty
    @Builder.Default
    private Integer timeout = 30;

    /**
     * Render connection with secrets resolved from RunContext.
     */
    public CobolConnection render(RunContext runContext) throws IllegalVariableEvaluationException {
        return CobolConnection.builder()
            .host(runContext.render(this.host))
            .user(runContext.render(this.user))
            .password(runContext.render(this.password))
            .port(this.port)
            .timeout(this.timeout)
            .build();
    }
}