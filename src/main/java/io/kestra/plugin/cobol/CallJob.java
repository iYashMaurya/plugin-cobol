package io.kestra.plugin.cobol;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.AS400Text;
import com.ibm.as400.access.ProgramCall;
import com.ibm.as400.access.ProgramParameter;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.cobol.models.JobInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Call an existing COBOL program synchronously on IBM i.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Call a COBOL program synchronously on IBM i",
    description = "Execute a COBOL program and wait for completion, capturing return codes and messages"
)
@Plugin(
    examples = {
        @Example(
            title = "Call a COBOL program with parameters",
            full = true,
            code = """
                id: call_cobol_program
                namespace: legacy.ibm_i

                tasks:
                  - id: run_interest_calc
                    type: io.kestra.plugin.cobol.CallJob
                    connection:
                      host: "{{ secret('IBMI_HOST') }}"
                      user: "{{ secret('IBMI_USER') }}"
                      password: "{{ secret('IBMI_PASSWORD') }}"
                    library: FINLIB
                    program: CALCINT
                    parameters:
                      - "2026-01-31"
                      - "CHECKING"
                """
        )
    }
)
public class CallJob extends AbstractCobolTask implements RunnableTask<CallJob.Output> {

    @Schema(
        title = "Program parameters",
        description = "List of string parameters to pass to the COBOL program"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private List<String> parameters = new ArrayList<>();

    @Schema(
        title = "Timeout duration",
        description = "Maximum time to wait for program completion"
    )
    @PluginProperty
    @Builder.Default
    private Duration taskTimeout = Duration.ofMinutes(5);

    @Override
    public Output run(RunContext runContext) throws Exception {
        Instant start = Instant.now();
        AS400 system = null;

        try {
            // Connect to IBM i
            system = connect(runContext);

            // Get program path
            String programPath = getProgramPath(runContext);
            runContext.logger().info("Calling program: {}", programPath);

            // Set up program call
            ProgramCall program = new ProgramCall(system);
            program.setProgram(programPath);

            // Set timeout
            if (timeout != null) {
                program.setThreadSafe(false);
            }

            // Prepare parameters
            ProgramParameter[] params = prepareParameters(runContext);
            if (params.length > 0) {
                program.setParameterList(params);
                runContext.logger().info("Set {} parameter(s)", params.length);
            }

            // Run the program
            runContext.logger().info("Executing program...");
            boolean success = program.run();

            // Capture messages
            List<String> messages = new ArrayList<>();
            AS400Message[] msgList = program.getMessageList();
            if (msgList != null) {
                for (AS400Message msg : msgList) {
                    String messageText = msg.getID() + ": " + msg.getText();
                    messages.add(messageText);
                    runContext.logger().info("Message: {}", messageText);
                }
            }

            // Get job information
            JobInfo jobInfo = null;
            try {
                com.ibm.as400.access.Job job = program.getServerJob();
                if (job != null) {
                    jobInfo = JobInfo.from(
                        job.getNumber(),
                        job.getUser(),
                        job.getName()
                    );
                }
            } catch (Exception e) {
                runContext.logger().warn("Could not retrieve job info: {}", e.getMessage());
            }

            Duration duration = Duration.between(start, Instant.now());

            runContext.logger().info(
                "Program completed with status: {} in {}ms",
                success ? "SUCCESS" : "FAILED",
                duration.toMillis()
            );

            return Output.builder()
                .returnCode(success ? 0 : 1)
                .success(success)
                .messages(messages)
                .job(jobInfo)
                .duration(duration)
                .build();

        } finally {
            disconnect(system, runContext);
        }
    }

    /**
     * Prepare program parameters from string list.
     */
    private ProgramParameter[] prepareParameters(RunContext runContext) throws Exception {
        List<String> renderedParams = runContext.render(this.parameters);

        ProgramParameter[] params = new ProgramParameter[renderedParams.size()];
        for (int i = 0; i < renderedParams.size(); i++) {
            String value = renderedParams.get(i);
            // Use 256 as default parameter length (can be made configurable)
            AS400Text textConverter = new AS400Text(256);
            byte[] data = textConverter.toBytes(value);
            params[i] = new ProgramParameter(data);
        }

        return params;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Return code",
            description = "0 for success, non-zero for errors"
        )
        private Integer returnCode;

        @Schema(
            title = "Success status"
        )
        private Boolean success;

        @Schema(
            title = "Messages from the program execution"
        )
        private List<String> messages;

        @Schema(
            title = "Job information"
        )
        private JobInfo job;

        @Schema(
            title = "Execution duration"
        )
        private Duration duration;
    }
}