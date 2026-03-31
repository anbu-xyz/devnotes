package uk.anbu.devnotes.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GroovyShellRunner {

    public static String execute(String script) throws Exception {
        groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell();
        log.debug("Executing Groovy script");
        log.trace("Script source:\n{}", script);
        Object result = shell.evaluate(script);
        log.debug("Finished executing Groovy script");
        log.trace("Script result:\n{}", result);
        return result != null ? result.toString() : "";
    }

}
