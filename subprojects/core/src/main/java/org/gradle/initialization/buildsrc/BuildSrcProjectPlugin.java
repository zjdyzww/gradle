/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.initialization.buildsrc;

import org.gradle.api.Plugin;
import org.gradle.api.internal.artifacts.PublishArtifactInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.project.PluginsProjectConfigureActions;
import org.gradle.configuration.project.ProjectConfigureAction;
import org.gradle.internal.service.CachingServiceLocator;

import javax.inject.Inject;

public abstract class BuildSrcProjectPlugin implements Plugin<ProjectInternal> {

    @Inject
    public abstract CachingServiceLocator getCachingServiceLocator();

    @Override
    public void apply(ProjectInternal project) {
        // TODO: Deprecate this behavior
        // TODO: Delete all of BuildSrcProjectConfigurationAction
        ProjectConfigureAction action = PluginsProjectConfigureActions.of(
                BuildSrcProjectConfigurationAction.class,
                getCachingServiceLocator());
        action.execute(project);

        // TODO: buildSrc publications need to require "build" to run

        // TODO: Need to expose buildSrc classpath to root project
        // EVIL!
        project.getGradle().getParent().getRootProject().getBuildscript().getDependencies().add("classpath", project);
    }
}
