package io.kestra.plugin.cobol.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

/**
 * Information about an IBM i job.
 */
@Builder
@Getter
public class JobInfo {

    @Schema(
        title = "Job name"
    )
    private String name;

    @Schema(
        title = "Job number"
    )
    private String number;

    @Schema(
        title = "Job user profile"
    )
    private String user;

    @Schema(
        title = "Fully qualified job identifier",
        description = "Format: number/user/name"
    )
    private String qualifiedJobName;

    /**
     * Create JobInfo from JTOpen Job components.
     */
    public static JobInfo from(String number, String user, String name) {
        return JobInfo.builder()
            .name(name)
            .number(number)
            .user(user)
            .qualifiedJobName(number + "/" + user + "/" + name)
            .build();
    }
}