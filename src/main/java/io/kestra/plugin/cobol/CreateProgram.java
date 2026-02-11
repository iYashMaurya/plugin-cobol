package io.kestra.plugin.cobol;

import com.ibm.as400.access.*;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.cobol.models.SourceConfig;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;
import lombok.experimental.SuperBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Create and compile a COBOL program on IBM i from source.
 */
@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Create and compile a COBOL program on IBM i",
    description = "Upload COBOL source and compile it using CRTCBLPGM command"
)
@Plugin(
    examples = {
        @Example(
            title = "Compile COBOL program from inline source",
            full = true,
            code = """
                id: compile_cobol_inline
                namespace: legacy.ibm_i

                tasks:
                  - id: create_program
                    type: io.kestra.plugin.cobol.CreateProgram
                    connection:
                      host: "{{ secret('IBMI_HOST') }}"
                      user: "{{ secret('IBMI_USER') }}"
                      password: "{{ secret('IBMI_PASSWORD') }}"
                    library: TESTLIB
                    program: HELLO
                    source:
                      inline: |
                        IDENTIFICATION DIVISION.
                        PROGRAM-ID. HELLO.
                        PROCEDURE DIVISION.
                            DISPLAY 'HELLO WORLD'.
                            STOP RUN.
                """
        ),
        @Example(
            title = "Compile COBOL program from URL",
            full = true,
            code = """
                id: compile_cobol_from_url
                namespace: legacy.ibm_i

                tasks:
                  - id: create_program
                    type: io.kestra.plugin.cobol.CreateProgram
                    connection:
                      host: "{{ secret('IBMI_HOST') }}"
                      user: "{{ secret('IBMI_USER') }}"
                      password: "{{ secret('IBMI_PASSWORD') }}"
                    library: FINLIB
                    program: CALCINT
                    source:
                      uri: https://repo.mybank.com/cobol/CALCINT.cbl
                    compileOptions: "DBGVIEW(*SOURCE)"
                """
        )
    }
)
public class CreateProgram extends AbstractCobolTask implements RunnableTask<CreateProgram.Output> {

    @Schema(
        title = "COBOL source configuration",
        description = "Specify either inline source or URI to source file"
    )
    @PluginProperty
    @NotNull
    @Valid
    private SourceConfig source;

    @Schema(
        title = "Compile options",
        description = "Additional options for CRTCBLPGM command (e.g., 'DBGVIEW(*SOURCE) OPTION(*SRCDBG)')"
    )
    @PluginProperty(dynamic = true)
    private String compileOptions;

    @Override
    public Output run(RunContext runContext) throws Exception {
        AS400 system = null;
        String sourceFilePath = null;

        try {
            // Validate source config
            source.validate();

            // Connect to IBM i
            system = connect(runContext);

            // Upload source to IFS
            sourceFilePath = uploadSource(system, runContext);
            runContext.logger().info("Source uploaded to: {}", sourceFilePath);

            // Compile the program
            String compileCommand = buildCompileCommand(runContext, sourceFilePath);
            runContext.logger().info("Compiling with command: {}", compileCommand);

            CommandCall cmd = new CommandCall(system);
            boolean success = cmd.run(compileCommand);

            // Capture compile messages
            List<String> messages = new ArrayList<>();
            AS400Message[] msgList = cmd.getMessageList();
            if (msgList != null) {
                for (AS400Message msg : msgList) {
                    String messageText = msg.getID() + ": " + msg.getText();
                    messages.add(messageText);

                    // Log based on message severity
                    String severity = msg.getSeverity() >= 30 ? "ERROR" :
                        msg.getSeverity() >= 10 ? "WARN" : "INFO";
                    runContext.logger().info("[{}] {}", severity, messageText);
                }
            }

            // Clean up source file from IFS
            try {
                deleteIFSFile(system, sourceFilePath);
                runContext.logger().info("Cleaned up temporary source file");
            } catch (Exception e) {
                runContext.logger().warn("Could not delete temporary file: {}", e.getMessage());
            }

            String programPath = getProgramPath(runContext);

            if (success) {
                runContext.logger().info("Program compiled successfully: {}", programPath);
            } else {
                runContext.logger().error("Program compilation failed");
            }

            return Output.builder()
                .success(success)
                .programPath(programPath)
                .compileMessages(messages)
                .sourceFile(sourceFilePath)
                .build();

        } finally {
            disconnect(system, runContext);
        }
    }

    /**
     * Upload source code to IFS.
     */
    private String uploadSource(AS400 system, RunContext runContext) throws Exception {
        String lib = runContext.render(this.library);
        String pgm = runContext.render(this.program);
        String fileName = String.format("%s_%s_%s.cbl", lib, pgm, System.currentTimeMillis());
        String ifsPath = getIFSPath(fileName);

        // Get source content
        String sourceContent;
        if (source.isInline()) {
            sourceContent = runContext.render(source.getInline());
        } else {
            sourceContent = downloadSourceFromUri(runContext);
        }

        // Write to IFS
        IFSFile ifsFile = new IFSFile(system, ifsPath);
        IFSFileOutputStream out = new IFSFileOutputStream(system, ifsPath);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8))) {
            writer.write(sourceContent);
        }

        return ifsPath;
    }

    /**
     * Download source from URI.
     */
    private String downloadSourceFromUri(RunContext runContext) throws Exception {
        String renderedUri = runContext.render(source.getUri());

        // Handle Kestra internal storage URIs
        if (renderedUri.startsWith("kestra://")) {
            try (InputStream is = runContext.storage().getFile(URI.create(renderedUri))) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            // Handle HTTP(S) URLs
            URI uri = URI.create(renderedUri);
            try (InputStream is = uri.toURL().openStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Build CRTCBLPGM command.
     */
    private String buildCompileCommand(RunContext runContext, String sourceFilePath) throws Exception {
        String lib = runContext.render(this.library);
        String pgm = runContext.render(this.program);

        StringBuilder cmd = new StringBuilder();
        cmd.append("CRTBNDCBL PGM(")
            .append(lib.toUpperCase())
            .append("/")
            .append(pgm.toUpperCase())
            .append(") SRCSTMF('")
            .append(sourceFilePath)
            .append("')");

        // Add compile options if specified
        if (compileOptions != null && !compileOptions.isEmpty()) {
            String renderedOptions = runContext.render(compileOptions);
            cmd.append(" ").append(renderedOptions);
        }

        return cmd.toString();
    }

    /**
     * Delete temporary file from IFS.
     */
    private void deleteIFSFile(AS400 system, String path) throws Exception {
        IFSFile file = new IFSFile(system, path);
        if (file.exists()) {
            file.delete();
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {

        @Schema(
            title = "Whether compilation was successful"
        )
        private Boolean success;

        @Schema(
            title = "Full path to the compiled program"
        )
        private String programPath;

        @Schema(
            title = "Compilation messages"
        )
        private List<String> compileMessages;

        @Schema(
            title = "Path to the source file in IFS (for debugging)"
        )
        private String sourceFile;
    }
}