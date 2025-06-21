package uk.anbu.devnotes.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroovyShellRunner {

    public static String execute(String script) {
        groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell();
        try {
            log.info("Executing Groovy script");
            log.trace("Script source:\n{}", script);
            Object result = shell.evaluate(script);
            log.info("Finished executing Groovy script");
            log.trace("Script result:\n{}", result);
            return result != null ? result.toString() : "";
        } catch (Exception e) {
            log.error("Error executing Groovy script", e);
            return "Error: " + e.getMessage();
        }
    }

}
