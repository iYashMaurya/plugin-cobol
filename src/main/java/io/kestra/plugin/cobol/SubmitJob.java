package io.kestra.plugin.cobol;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400Message;
import com.ibm.as400.access.CommandCall;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.cobol.models.JobInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Submit a COBOL job asynchronously on IBM i.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Submit a COBOL job asynchronously on IBM i",
    description = "Submit a batch job using SBMJOB command without waiting for completion"
)
@Plugin(
    examples = {
        @Example(
            title = "Submit end-of-day batch job",
            full = true,
            code = """
                id: submit_eod_batch
                namespace: banking.core

                tasks:
                  - id: submit_batch
                    type: io.kestra.plugin.cobol.SubmitJob
                    connection:
                      host: "{{ secret('IBMI_HOST') }}"
                      user: "{{ secret('IBMI_USER') }}"
                      password: "{{ secret('IBMI_PASSWORD') }}"
                    library: BATCHLIB
                    program: EODPROC
                    parameters:
                      - "2026-01-31"
                    job:
                      jobName: EODBATCH
                      jobQueue: QBATCH
                """
        )
    }
)
public class SubmitJob extends AbstractCobolTask implements RunnableTask<SubmitJob.Output> {

    @Schema(
        title = "Program parameters",
        description = "List of string parameters to pass to the COBOL program"
    )
    @PluginProperty(dynamic = true)
    @Builder.Default
    private List<String> parameters = new ArrayList<>();

    @Schema(
        title = "Job configuration"
    )
    @PluginProperty
    private JobConfig job;

    @Override
    public Output run(RunContext runContext) throws Exception {
        AS400 system = null;

        try {
            // Connect to IBM i
            system = connect(runContext);

            // Build SBMJOB command
            String command = buildSubmitJobCommand(runContext);
            runContext.logger().info("Submitting job with command: {}", command);

            // Execute command
            CommandCall cmd = new CommandCall(system);
            boolean success = cmd.run(command);

            // Capture messages
            List<String> messages = new ArrayList<>();
            AS400Message[] msgList = cmd.getMessageList();
            if (msgList != null) {
                for (AS400Message msg : msgList) {
                    String messageText = msg.getID() + ": " + msg.getText();
                    messages.add(messageText);
                    runContext.logger().info("Message: {}", messageText);
                }
            }

            // Try to extract job info from messages
            JobInfo jobInfo = extractJobInfo(msgList);

            if (success) {
                runContext.logger().info("Job submitted successfully");
            } else {
                runContext.logger().warn("Job submission may have failed, check messages");
            }

            return Output.builder()
                .submitted(success)
                .job(jobInfo)
                .messages(messages)
                .command(command)
                .build();

        } finally {
            disconnect(system, runContext);
        }
    }

    /**
     * Build the SBMJOB command with all parameters.
     */
    private String buildSubmitJobCommand(RunContext runContext) throws Exception {
        String lib = runContext.render(this.library);
        String pgm = runContext.render(this.program);
        List<String> renderedParams = runContext.render(this.parameters);

        StringBuilder cmd = new StringBuilder("SBMJOB CMD(CALL PGM(");
        cmd.append(lib.toUpperCase()).append("/").append(pgm.toUpperCase()).append(")");

        // Add parameters if any
        if (!renderedParams.isEmpty()) {
            cmd.append(" PARM(");
            String paramsStr = renderedParams.stream()
                .map(p -> "'" + p.replace("'", "''") + "'")  // Escape single quotes
                .collect(Collectors.joining(" "));
            cmd.append(paramsStr);
            cmd.append(")");
        }

        cmd.append(")");

        // Add job configuration if provided
        if (job != null) {
            if (job.getJobName() != null) {
                String jobName = runContext.render(job.getJobName());
                cmd.append(" JOB(").append(jobName).append(")");
            }
            if (job.getJobQueue() != null) {
                String jobQueue = runContext.render(job.getJobQueue());
                cmd.append(" JOBQ(").append(jobQueue).append(")");
            }
            if (job.getUserProfile() != null) {
                String userProfile = runContext.render(job.getUserProfile());
                cmd.append(" USER(").append(userProfile).append(")");
            }
        }

        return cmd.toString();
    }

    /**
     * Try to extract job information from submission messages.
     */
    private JobInfo extractJobInfo(AS400Message[] messages) {
        if (messages == null) {
            return null;
        }

        // Look for job submission message (typically CPF1124 or CPC1221)
        for (AS400Message msg : messages) {
            String text = msg.getText();
            // Try to parse "Job 123456/USER/JOBNAME submitted"
            if (text.contains("submitted") || text.contains("SBMJOB")) {
                // This is a simplified extraction - actual parsing would be more robust
                try {
                    // Message format varies, this is a basic implementation
                    if (text.contains("/")) {
                        String[] parts = text.split("\\s+");
                        for (String part : parts) {
                            if (part.contains("/") && part.split("/").length == 3) {
                                String[] jobParts = part.split("/");
                                return JobInfo.from(jobParts[0], jobParts[1], jobParts[2]);
                            }
                        }
                    }
                } catch (Exception e) {
                    // If parsing fails, just return null
                }
            }
        }

        return null;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobConfig {

        @Schema(
            title = "Job name",
            description = "Name for the submitted job"
        )
        @PluginProperty(dynamic = true)
        private String jobName;

        @Schema(
            title = "Job queue",
            description = "Queue where the job will be submitted (e.g., QBATCH)"
        )
        @PluginProperty(dynamic = true)
        private String jobQueue;

        @Schema(
            title = "User profile",
            description = "User profile under which the job will run"
        )
        @PluginProperty(dynamic = true)
        private String userProfile;
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Whether the job was submitted successfully"
        )
        private Boolean submitted;

        @Schema(
            title = "Job information if available"
        )
        private JobInfo job;

        @Schema(
            title = "Messages from the submission"
        )
        private List<String> messages;

        @Schema(
            title = "The SBMJOB command that was executed"
        )
        private String command;
    }
}