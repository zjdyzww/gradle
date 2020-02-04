/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.plugins.quality.internal

import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ant.BasicAntBuilder
import org.gradle.api.plugins.quality.PmdReports
import org.gradle.api.specs.Spec
import org.gradle.internal.Cast
import org.gradle.internal.Factory
import org.gradle.internal.SystemProperties
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.util.VersionNumber
import org.gradle.workers.WorkAction
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Field

abstract class PmdInvoker implements WorkAction<PmdParameters> {
    private static final Logger LOGGER = LoggerFactory.getLogger(PmdInvoker.class)
    private final PmdParameters pmdParameters
    private final PmdReports reports
    private final org.gradle.api.AntBuilder antBuilder

    PmdInvoker(PmdParameters parameters, PmdReports reports) {
        this.parameters = parameters;
        this.reports = reports;
        this.antBuilder = new BasicAntBuilder();
    }

    @Override
    PmdParameters getParameters() {
        return pmdParameters
    }

    @Override
    void execute() {
        def pmdClasspath = parameters.pmdClasspath.filter(new FileExistFilter())
        def ruleSets = parameters.ruleSets.get()
        def ruleSetConfig = parameters.ruleSetConfig.get().asFile
        def classpath = parameters.classpath.filter(new FileExistFilter())
        def incrementalAnalysis = parameters.incrementalAnalysis.get()

        // PMD uses java.class.path to determine it's implementation classpath for incremental analysis
        // Since we run PMD inside the Gradle daemon, this pulls in all of Gradle's runtime.
        // To hide this from PMD, we override the java.class.path to just the PMD classpath from Gradle's POV.

        SystemProperties.instance.withSystemProperty("java.class.path", pmdClasspath.files.join(File.pathSeparator), new Factory<Void>() {
            @Override
            Void create() {
                antBuilder.withClasspath(pmdClasspath).execute { a ->
                    VersionNumber version = determinePmdVersion(Thread.currentThread().getContextClassLoader())

                    def antPmdArgs = [
                            failOnRuleViolation: false,
                            failuresPropertyName: "pmdFailureCount",
                            minimumPriority: parameters.rulePriority.get(),
                    ]

                    String htmlFormat = "html"
                    if (version < VersionNumber.parse("5.0.0")) {
                        // <5.x
                        // NOTE: PMD 5.0.2 apparently introduces an element called "language" that serves the same purpose
                        // http://sourceforge.net/p/pmd/bugs/1004/
                        // http://java-pmd.30631.n5.nabble.com/pmd-pmd-db05bc-pmd-AntTask-support-for-language-td5710041.html
                        antPmdArgs["targetjdk"] = parameters.targetJdk.get().name

                        htmlFormat = "betterhtml"

                        // fallback to basic on pre 5.0 for backwards compatible
                        if (ruleSets == ["java-basic"] || ruleSets == ["category/java/errorprone.xml"]) {
                            ruleSets = ['basic']
                        }
                        if (incrementalAnalysis) {
                            assertUnsupportedIncrementalAnalysis()
                        }
                    } else if (version < VersionNumber.parse("6.0.0")) {
                        // 5.x
                        if (ruleSets == ["category/java/errorprone.xml"]) {
                            ruleSets = ['java-basic']
                        }
                        if (incrementalAnalysis) {
                            assertUnsupportedIncrementalAnalysis()
                        }
                    } else {
                        // 6.+
                        if (incrementalAnalysis) {
                            antPmdArgs["cacheLocation"] = parameters.incrementalCacheFile.get().asFile
                        } else {
                            if (version >= VersionNumber.parse("6.2.0")) {
                                antPmdArgs['noCache'] = true
                            }
                        }
                    }

                    ant.taskdef(name: 'pmd', classname: 'net.sourceforge.pmd.ant.PMDTask')
                    ant.pmd(antPmdArgs) {
                        parameters.source.addToAntBuilder(ant, 'fileset', FileCollection.AntType.FileSet)
                        ruleSets.each {
                            ruleset(it)
                        }
                        parameters.ruleSetFiles.each {
                            ruleset(it)
                        }
                        if (ruleSetConfig != null) {
                            ruleset(ruleSetConfig.asFile())
                        }

                        if (classpath != null) {
                            classpath.addToAntBuilder(ant, 'auxclasspath', FileCollection.AntType.ResourceCollection)
                        }

                        if (reports.html.enabled) {
                            assert reports.html.destination.parentFile.exists()
                            formatter(type: htmlFormat, toFile: reports.html.destination)
                        }
                        if (reports.xml.enabled) {
                            formatter(type: 'xml', toFile: reports.xml.destination)
                        }

                        if (parameters.consoleOutput.get()) {
                            def consoleOutputType = 'text'
                            if (parameters.stdOutIsAttachedToTerminal.get()) {
                                consoleOutputType = 'textcolor'
                            }
                            a.builder.saveStreams = false
                            formatter(type: consoleOutputType, toConsole: true)
                        }
                    }
                    def failureCount = ant.project.properties["pmdFailureCount"]
                    if (failureCount) {
                        def message = "$failureCount PMD rule violations were found."
                        def report = reports.firstEnabled
                        if (report) {
                            def reportUrl = new ConsoleRenderer().asClickableFileUrl(report.destination)
                            message += " See the report at: $reportUrl"
                        }
                        if (parameters.ignoreFailures.get()) {
                            LOGGER.warn(message)
                        } else {
                            throw new GradleException(message)
                        }
                    }
                }

                return null
            }

            private VersionNumber determinePmdVersion(ClassLoader antLoader) {
                Class pmdVersion
                try {
                    pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMDVersion")
                } catch (ClassNotFoundException e) {
                    pmdVersion = antLoader.loadClass("net.sourceforge.pmd.PMD")
                }
                Field versionField = pmdVersion.getDeclaredField("VERSION")
                return VersionNumber.parse(Cast.castNullable(String.class, versionField.get(null)))
            }

            private void assertUnsupportedIncrementalAnalysis() {
                throw new GradleException("Incremental analysis only supports PMD 6.0.0 and newer")
            }
        })
    }

    private static class FileExistFilter implements Spec<File> {
        @Override
        boolean isSatisfiedBy(File element) {
            return element.exists()
        }
    }
}
