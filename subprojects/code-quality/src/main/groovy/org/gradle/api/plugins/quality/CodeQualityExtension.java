/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.plugins.quality;

import org.gradle.api.Incubating;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.deprecation.DeprecationLogger;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Collection;

/**
 * Base Code Quality Extension.
 */
public abstract class CodeQualityExtension {

    private String toolVersion;
    private Collection<SourceSet> sourceSets;
    private boolean ignoreFailures;

    @Nullable
    private final DirectoryProperty reportsDirectory;

    @Deprecated
    private File reportsDir;

    @Deprecated
    public CodeQualityExtension() {
        DeprecationLogger.deprecateMethod(CodeQualityExtension.class, "constructor CodeQualityExtension()")
            .replaceWith("constructor CodeQualityExtension(ObjectFactory)")
            .willBeRemovedInGradle8()
            .withUpgradeGuideSection(6, "TODO") // TODO
            .nagUser();
        reportsDirectory = null;
    }

    /**
     * Creates a new code quality extension.
     *
     * @since 6.8
     */
    @Incubating // TODO no non-deprecated replacement ...
    public CodeQualityExtension(ObjectFactory objects) {
        reportsDirectory = objects.directoryProperty();
    }

    /**
     * The version of the code quality tool to be used.
     */
    public String getToolVersion() {
        return toolVersion;
    }

    /**
     * The version of the code quality tool to be used.
     */
    public void setToolVersion(String toolVersion) {
        this.toolVersion = toolVersion;
    }

    /**
     * The source sets to be analyzed as part of the <tt>check</tt> and <tt>build</tt> tasks.
     */
    public Collection<SourceSet> getSourceSets() {
        return sourceSets;
    }

    /**
     * The source sets to be analyzed as part of the <tt>check</tt> and <tt>build</tt> tasks.
     */
    public void setSourceSets(Collection<SourceSet> sourceSets) {
        this.sourceSets = sourceSets;
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    public boolean isIgnoreFailures() {
        return ignoreFailures;
    }

    /**
     * Whether to allow the build to continue if there are warnings.
     *
     * Example: ignoreFailures = true
     */
    public void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures;
    }

    /**
     * The directory where reports will be generated.
     *
     * @since 6.8
     */
    public DirectoryProperty getReportsDirectory() {
        if (reportsDirectory == null) {
            throw new IllegalStateException(
                "This plugin uses a deprecated constructor of " + getClass().getName() + ". " +
                    "You need to use the deprecated `reportsDir` property instead."
            );
        }
        return reportsDirectory;
    }

    /**
     * The directory where reports will be generated.
     */
    @Deprecated
    public File getReportsDir() {
        nagReportsDirPropertyDeprecation();
        if (reportsDirectory != null) {
            return reportsDirectory.get().getAsFile();
        }
        return reportsDir;
    }

    /**
     * The directory where reports will be generated.
     */
    @Deprecated
    public void setReportsDir(File reportsDir) {
        nagReportsDirPropertyDeprecation();
        if (reportsDirectory != null) {
            reportsDirectory.set(reportsDir);
        } else {
            this.reportsDir = reportsDir;
        }
    }

    private void nagReportsDirPropertyDeprecation() {
        DeprecationLogger.deprecateProperty(getConcreteType(), "reportsDir")
            .replaceWith("reportsDirectory")
            .willBeRemovedInGradle8()
            .withDslReference(getConcreteType(), "reportsDirectory")
            .nagUser();
    }

    /**
     * The concrete type of the extension, for documentation purpose.
     *
     * @since 6.8
     */
    @Incubating
    protected Class<? extends CodeQualityExtension> getConcreteType() {
        return CodeQualityExtension.class;
    }
}
