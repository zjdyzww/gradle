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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.consistency;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolutionResult;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.artifacts.result.UnresolvedDependencyResult;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributesSchema;
import org.gradle.api.internal.artifacts.DependencyResolutionServices;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyConstraint;
import org.gradle.api.internal.attributes.AttributesSchemaInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.OverridableAttributesSchema;
import org.gradle.internal.Cast;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import static java.util.stream.Collectors.*;

public class DefaultCrossProjectResolutionConsistencyService implements CrossProjectResolutionConsistencyService {

    private static final String GLOBAL_DEPENDENCY_RESOLUTION_CONSISTENCY_CONFIG = "globalDependencyResolutionConsistency";

    private final Lock lock = new ReentrantLock();
    private final Set<Project> registeredProjects = Sets.newHashSet();
    private final BuildOperationExecutor buildOperationExecutor;
    private final Supplier<DependencyResolutionServices> dependencyResolutionServicesSupplier;

    private final AtomicBoolean resolved = new AtomicBoolean();
    private final Map<Key, Result> constraints = Maps.newHashMap();

    private Project rootProject;
    private int resolveCount;

    public DefaultCrossProjectResolutionConsistencyService(BuildOperationExecutor buildOperationExecutor, Supplier<DependencyResolutionServices> dependencyResolutionServicesSupplier) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.dependencyResolutionServicesSupplier = dependencyResolutionServicesSupplier;
    }

    @Override
    public void optIntoGlobalConsistency(Project project) {
        if (rootProject == null) {
            rootProject = project.getRootProject();
        }
        if (registeredProjects.add(project)) {
            doRegisterProject(project);
        }
    }

    @Override
    public void resolve(ImmutableAttributes attributes, AttributesSchemaInternal attributesSchema, CrossProjectConsistencyResolutionBuilder result) {
        try {
            Key key = new Key(attributesSchema, attributes);
            lock.lock();
            Result cachedResult = constraints.computeIfAbsent(key, this::doBuildConstraints);
            for (ModuleVersionIdentifier identifier : cachedResult.resolved) {
                result.resolved(identifier);
            }
            for (ModuleIdentifier moduleIdentifier : cachedResult.unresolved.keySet()) {
                result.unresolved(moduleIdentifier);
            }
        } finally {
            lock.unlock();
        }
    }

    private Result doBuildConstraints(Key key) {
        DependencyResolutionServices resolutionServices = dependencyResolutionServicesSupplier.get();
        ImmutableAttributes attributes = key.attributes;
        AttributesSchemaInternal attributesSchema = key.attributesSchema;
        AttributesSchema baseSchema = resolutionServices.getAttributesSchema();
        if (baseSchema instanceof OverridableAttributesSchema) {
            return ((OverridableAttributesSchema) baseSchema).withOverride(attributesSchema, () -> performGlobalResolution(resolutionServices, attributes));
        }
        return Result.empty();
    }

    private Result performGlobalResolution(DependencyResolutionServices resolutionServices, ImmutableAttributes attributes) {
        return buildOperationExecutor.call(new CallableBuildOperation<Result>() {
            @Override
            public Result call(BuildOperationContext context) throws Exception {
                Configuration uberConf = resolutionServices.getConfigurationContainer()
                    .create(nextConfigurationName())
                    .attributes(attrs -> {
                        for (Attribute<?> attribute : attributes.keySet()) {
                            Object value = attributes.getAttribute(attribute);
                            attrs.attribute(attribute, Cast.uncheckedCast(value));
                        }
                    });
                rootProject.getAllprojects()
                    .forEach(p -> {
                        synchronized (uberConf) {
                            registerProjectConfigurations(p, uberConf);
                        }
                    });

                ResolutionResult resolutionResult = uberConf
                    .getIncoming()
                    .getResolutionResult();
                Map<ModuleIdentifier, Set<ModuleComponentSelector>> errors = collectErrors(resolutionResult);
                List<ModuleVersionIdentifier> resolved = collectResolved(resolutionResult);
                return new Result(resolved, errors);
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Resolving cross-project consistency resolution constraints");
            }
        });
    }

    private List<ModuleVersionIdentifier> collectResolved(ResolutionResult resolutionResult) {
        return resolutionResult
            .getAllDependencies()
            .stream()
            .filter(ResolvedDependencyResult.class::isInstance)
            .map(ResolvedDependencyResult.class::cast)
            .map(ResolvedDependencyResult::getSelected)
            .filter(c -> c.getId() instanceof ModuleComponentIdentifier)
            .map(ResolvedComponentResult::getModuleVersion)
            .collect(toList());
    }

    private Map<ModuleIdentifier, Set<ModuleComponentSelector>> collectErrors(ResolutionResult resolutionResult) {
        return resolutionResult.getAllDependencies()
            .stream()
            .filter(UnresolvedDependencyResult.class::isInstance)
            .map(DependencyResult::getRequested)
            .filter(c -> !(c instanceof ProjectComponentSelector))
            .filter(ModuleComponentSelector.class::isInstance)
            .map(ModuleComponentSelector.class::cast)
            .collect(groupingBy(ModuleComponentSelector::getModuleIdentifier, toCollection(LinkedHashSet::new)));
    }

    private String nextConfigurationName() {
        try {
            if (resolveCount == 0) {
                return GLOBAL_DEPENDENCY_RESOLUTION_CONSISTENCY_CONFIG;
            }
            return GLOBAL_DEPENDENCY_RESOLUTION_CONSISTENCY_CONFIG + resolveCount;
        } finally {
            resolveCount++;
        }
    }

    private void doRegisterProject(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();

        // Before _any_ configuration of this project is resolved, we need to collect dependencies
        // of _all_ configurations of _all_ projects. And we must fail if a new configuration is
        // supposed to participate in global consistency but was created _after_ the global
        // resolution was performed

        configurations.all(conf -> {
            conf.getIncoming().beforeResolve(rd -> {
                ConfigurationInternal cnf = (ConfigurationInternal) conf;
                Configuration consistentResolutionSource = cnf.getConsistentResolutionSource();
                if (consistentResolutionSource != null) {
                    if (resolved.get()) {
                        throw new InvalidUserCodeException("TODO");
                    }
                }
            });
        });
    }

    private void registerProjectConfigurations(Project project, Configuration target) {
        for (Configuration conf : project.getConfigurations()) {
            if (conf != target) {
                ConfigurationInternal cnf = (ConfigurationInternal) conf;
                Configuration consistentResolutionSource = cnf.getConsistentResolutionSource();
                if (consistentResolutionSource != null) {
                    registerDependencies(target, cnf);
                    registerDependencies(target, consistentResolutionSource);
                }
            }
        }
    }

    private static void registerDependencies(Configuration global, Configuration source) {
        source.getAllDependencies().forEach(d -> {
            if (!(d instanceof ProjectDependency)) {
                global.getDependencies().add(d);
            }
        });
        source.getAllDependencyConstraints().forEach(dc -> {
            if (!(dc instanceof ProjectDependencyConstraint)) {
                global.getDependencyConstraints().add(dc);
            }
        });
    }

    private static class Key {
        private final AttributesSchemaInternal attributesSchema;
        private final ImmutableAttributes attributes;

        private Key(AttributesSchemaInternal attributesSchema, ImmutableAttributes attributes) {
            this.attributesSchema = attributesSchema;
            this.attributes = attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            Key that = (Key) o;

            if (!attributesSchema.equals(that.attributesSchema)) {
                return false;
            }
            return attributes.equals(that.attributes);
        }

        @Override
        public int hashCode() {
            int result = attributesSchema.hashCode();
            result = 31 * result + attributes.hashCode();
            return result;
        }
    }

    private static class Result {
        private final static Result EMPTY = new Result(Collections.emptyList(), Collections.emptyMap());

        private final List<ModuleVersionIdentifier> resolved;
        private final Map<ModuleIdentifier, Set<ModuleComponentSelector>> unresolved;

        private Result(List<ModuleVersionIdentifier> resolved, Map<ModuleIdentifier, Set<ModuleComponentSelector>> unresolved) {
            this.resolved = resolved;
            this.unresolved = unresolved;
        }

        public static Result empty() {
            return EMPTY;
        }
    }
}
