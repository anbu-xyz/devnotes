#!/usr/bin/env groovy

import groovy.xml.XmlSlurper

import java.util.concurrent.TimeUnit

String runGitCommand(String command) {
    def os = System.getProperty('os.name').toLowerCase()
    Process proc
    StringBuilder output = new StringBuilder()
    StringBuilder error = new StringBuilder()
    if (os.contains('win')) {
        System.out.println("Running on windows: ${command}")
        proc = ["cmd.exe", "/c", command].execute()
    } else {
        System.out.println("Running in shell: ${command}")
        proc = ["sh", "-c", command].execute()
    }
    proc.consumeProcessOutput(output, error)
    proc.waitFor(10, TimeUnit.SECONDS)

    if (proc.exitValue() != 0) {
        def errorMessage = "ERROR: Git command failed."
        if (error != null && !error.isEmpty()) {
            errorMessage += "\nError output: ${error}"
        }
        if (output != null && !output.isEmpty()) {
            errorMessage += "\nOutput: ${output}"
        }
        System.err.println(errorMessage)
        System.exit(1)
    }
    return output.toString()
}

def extractVersion(String pomContent) {
    def pomXml = new XmlSlurper().parseText(pomContent)
    // Return the first <version> tag that is not inside <parent>
    def parentVersion = pomXml.parent?.version?.text()
    def projectVersion = pomXml.version?.text()
    if (projectVersion && (!parentVersion || projectVersion != parentVersion)) {
        return projectVersion
    }
    return null
}

def mainPomFile(baseDir) {
    // in this case, the pom.xml is in the root directory
    return "pom.xml"
    // return "${baseDir.name}/pom.xml"
}

def "check if pom was changed"(baseDir) {
    def changedFiles = runGitCommand('git diff --name-only HEAD~1 HEAD')
    if (!changedFiles.readLines().any {
        it.trim() == mainPomFile(baseDir)
    }) {
        System.err.println('ERROR: pom.xml was not changed in the latest commit. Version must be updated.')
        System.exit(1)
    }
}

def "check if version was changed"(baseDir) {
    String pomHead = runGitCommand("git --no-pager show HEAD:" + mainPomFile(baseDir))
    String pomPrev = runGitCommand("git --no-pager show HEAD~1:" + mainPomFile(baseDir))
    def versionHead = extractVersion(pomHead)
    def versionPrev = extractVersion(pomPrev)
    if (!versionHead || !versionPrev) {
        System.err.println('ERROR: Could not extract <version> from pom.xml in one of the commits.')
        System.exit(1)
    }
    if (versionHead == versionPrev) {
        System.err.println('ERROR: pom.xml <version> was not updated in the latest commit. Version must be incremented.')
        System.exit(1)
    }
    System.out.println(new String("pom.xml version updated: $versionPrev -> $versionHead"))
}

def "check for uncommitted changes"() {
    def status = runGitCommand('git status --porcelain')
    if (status && !status.trim().isEmpty()) {
        System.err.println('ERROR: You have uncommitted changes. Please commit or stash them before running this script.')
        System.exit(1)
    }
}

def baseDir = this['project']['basedir']

System.out.println "Checking for uncommited changes"
"check for uncommitted changes"()
System.out.println "Going to check if pom.xml has changed"
"check if pom was changed"(baseDir)
System.out.println "Going to check if version tag is updated in pom.xml"
"check if version was changed"(baseDir)