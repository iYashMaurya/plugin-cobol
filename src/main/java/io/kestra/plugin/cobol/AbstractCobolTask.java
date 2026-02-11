package io.kestra.plugin.cobol;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.cobol.models.CobolConnection;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.beans.PropertyVetoException;
import java.io.IOException;

/**
 * Base class for all COBOL tasks providing connection management.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractCobolTask extends Task {

    @Schema(
        title = "IBM i connection configuration"
    )
    @PluginProperty
    @NotNull
    @Valid
    protected CobolConnection connection;

    @Schema(
        title = "Library name",
        description = "The IBM i library containing the program"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    protected String library;

    @Schema(
        title = "Program name",
        description = "The COBOL program name (without .PGM extension)"
    )
    @PluginProperty(dynamic = true)
    @NotNull
    protected String program;

    /**
     * Establish connection to IBM i system.
     */
    protected AS400 connect(RunContext runContext) throws IllegalVariableEvaluationException {
        CobolConnection conn = this.connection.render(runContext);

        try {
            AS400 system = new AS400(conn.getHost(), conn.getUser(), conn.getPassword());

            // Set timeout if specified
            if (conn.getTimeout() != null) {
                system.setDefaultUser(conn.getUser(), conn.getPassword());
                system.setGuiAvailable(false);
            }

            runContext.logger().info(
                "Connected to IBM i system: {} as user: {}",
                conn.getHost(),
                conn.getUser()
            );

            return system;

        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to IBM i system: " + e.getMessage(), e);
        }
    }

    /**
     * Safely disconnect from IBM i system.
     */
    protected void disconnect(AS400 system, RunContext runContext) {
        if (system != null) {
            try {
                system.disconnectAllServices();
                runContext.logger().info("Disconnected from IBM i system");
            } catch (Exception e) {
                runContext.logger().warn("Error during disconnect: {}", e.getMessage());
            }
        }
    }

    /**
     * Build qualified program path.
     */
    protected String getProgramPath(RunContext runContext) throws IllegalVariableEvaluationException {
        String lib = runContext.render(this.library);
        String pgm = runContext.render(this.program);
        return String.format("/QSYS.LIB/%s.LIB/%s.PGM", lib.toUpperCase(), pgm.toUpperCase());
    }

    /**
     * Build IFS file path for temporary files.
     */
    protected String getIFSPath(String fileName) {
        return String.format("/tmp/%s", fileName);
    }
}