/**
 * Kestra plugin for IBM i COBOL program and job management.
 *
 * <p>This plugin provides tasks to interact with COBOL programs on IBM i systems
 * using JTOpen (IBM Toolbox for Java). It enables orchestration of legacy COBOL
 * applications and batch jobs within modern Kestra workflows.</p>
 *
 * <h2>Main Tasks</h2>
 * <ul>
 *   <li>{@link io.kestra.plugin.cobol.CallJob} - Call a COBOL program synchronously</li>
 *   <li>{@link io.kestra.plugin.cobol.SubmitJob} - Submit a COBOL job asynchronously</li>
 *   <li>{@link io.kestra.plugin.cobol.CreateProgram} - Create and compile a COBOL program</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * id: cobol_workflow
 * namespace: legacy.banking
 *
 * tasks:
 *   - id: compile_program
 *     type: io.kestra.plugin.cobol.CreateProgram
 *     connection:
 *       host: "{{ secret('IBMI_HOST') }}"
 *       user: "{{ secret('IBMI_USER') }}"
 *       password: "{{ secret('IBMI_PASSWORD') }}"
 *     library: FINLIB
 *     program: CALCINT
 *     source:
 *       uri: https://repo.example.com/cobol/CALCINT.cbl
 *
 *   - id: run_program
 *     type: io.kestra.plugin.cobol.CallJob
 *     connection:
 *       host: "{{ secret('IBMI_HOST') }}"
 *       user: "{{ secret('IBMI_USER') }}"
 *       password: "{{ secret('IBMI_PASSWORD') }}"
 *     library: FINLIB
 *     program: CALCINT
 *     parameters:
 *       - "2026-01-31"
 * }</pre>
 *
 * @see <a href="https://ibm.github.io/JTOpen">JTOpen Documentation</a>
 */
@PluginSubGroup(
    title = "IBM i COBOL",
    description = "Execute and manage COBOL programs on IBM i systems",
    categories = { PluginSubGroup.PluginCategory.BATCH }
)
package io.kestra.plugin.cobol;

import io.kestra.core.models.annotations.PluginSubGroup;