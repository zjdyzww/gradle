/*
 * Copyright 2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.jvm.toolchain

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf
import spock.lang.TempDir

import java.util.regex.Pattern

class InvalidJvmInstallationReportingIntegrationTest extends AbstractIntegrationSpec {

    @IgnoreIf({ GradleContextualExecuter.isNoDaemon() || GradleContextualExecuter.isConfigCache() || AvailableJavaHomes.differentJdk == null })
    def "invalid JDK is cached only for current build if in daemon"() {
        // Require a different JDK to be able to find the logs of its probing for system properties
        def existingJdk = AvailableJavaHomes.differentJdk

        def invalidJdkHome1 = new File(temporaryDirectory, "jdk1")
        def invalidJdkHome2 = new File(temporaryDirectory, "jdk2")
        assert(invalidJdkHome1.mkdirs())
        assert(invalidJdkHome2.mkdirs())

        // The builds should trigger toolchains discovery; here it's done from different subprojects in order to test
        // that the JVM installation detection is cached during a build
        multiProjectBuild("invalidJvmInstallationReporting", ["sub1", "sub2"])
        buildFile << """
            import org.gradle.jvm.toolchain.internal.JavaInstallationRegistry;

            subprojects {
                apply plugin: 'jvm-toolchains'
                tasks.register('exec', JavaExec) {
                    javaLauncher.set(javaToolchains.launcherFor {
                        languageVersion = JavaLanguageVersion.of(${existingJdk.javaVersion.majorVersion})
                    })
                    mainClass.set("None")
                    jvmArgs = ['-version']
                }
            }
        """

        when: "running two consecutive builds in a daemon"
        def results = (0..1).collect {
            executer
                .withArgument("-Porg.gradle.java.installations.paths=$invalidJdkHome1.canonicalPath,$invalidJdkHome2.canonicalPath,$existingJdk.javaHome.absolutePath")
                .withArgument("--info")
                .requireIsolatedDaemons()
                .withStackTraceChecksDisabled() // expect the info logs from JVM metadata detector to contain the stack trace
                .withTasks(":sub1:exec", ":sub2:exec")
                .run()
        }

        then: "invalid JVM installation warning should be printed in every build"
        results.size() == 2
        results.every { result ->
            1 == countMatches(
                "Invalid Java installation found at '${invalidJdkHome1.canonicalPath}' (system property 'org.gradle.java.installations.paths'). " +
                    "It will be re-checked in the next build. This might have performance impact if it keeps failing. " +
                    "Run the 'javaToolChains' task for more details.",
                result.plainTextOutput
            )
            1 == countMatches(
                "Invalid Java installation found at '${invalidJdkHome2.canonicalPath}' (system property 'org.gradle.java.installations.paths'). " +
                    "It will be re-checked in the next build. This might have performance impact if it keeps failing. " +
                    "Run the 'javaToolChains' task for more details.",
                result.plainTextOutput
            )
        }
        // valid JVM installation metadata should be cached across the builds
        def metadataAccessMarker = "Received JVM installation metadata from '$existingJdk.javaHome.absolutePath'"
        1 == countMatches(metadataAccessMarker, results[0].plainTextOutput)
        0 == countMatches(metadataAccessMarker, results[1].plainTextOutput)
    }

    @TempDir
    File temporaryDirectory

    private int countMatches(String pattern, String text) {
        return Pattern.compile(Pattern.quote(pattern)).matcher(text).results().count()
    }
}
