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
package org.gradle.internal.execution.impl;

import org.gradle.api.Action;
import org.gradle.internal.Cast;
import org.gradle.internal.execution.CachingResult;
import org.gradle.internal.execution.ExecutionEngine;
import org.gradle.internal.execution.ExecutionEngine2;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;

import java.lang.reflect.ParameterizedType;

public class DefaultExecutionEngine2 implements ExecutionEngine2 {
    private final ExecutionEngine delegate;
    private final InstantiatorFactory instantiatorFactory;
    private final ServiceRegistry serviceRegistry;

    public DefaultExecutionEngine2(ExecutionEngine delegate,
                                   InstantiatorFactory instantiatorFactory,
                                   ServiceRegistry serviceRegistry) {
        this.delegate = delegate;
        this.instantiatorFactory = instantiatorFactory;
        this.serviceRegistry = serviceRegistry;
    }


    @Override
    public CommandBuilder prepare() {
        return new DefaultCommandBuilder();
    }

    private class DefaultCommandBuilder implements CommandBuilder {

        @Override
        public CommandInWorkspace workspace(ImmutableWorkspaceProvider workspace) {
            return new DefaultCommandInWorkspace(workspace);
        }
    }

    private class DefaultCommandInWorkspace implements CommandInWorkspace {
        private final ImmutableWorkspaceProvider workspace;

        private DefaultCommandInWorkspace(ImmutableWorkspaceProvider workspace) {
            this.workspace = workspace;
        }

        @Override
        public <T extends ConfigurableUnitOfWork.Params> CommandReadyToExecute workUnit(Class<? extends ConfigurableUnitOfWork<T>> unitOfWorkClass, Action<? super T> spec) {
            return new DefaultCommandReadyToExecute<T>(workspace, unitOfWorkClass, spec);
        }
    }

    private class DefaultCommandReadyToExecute<T extends ConfigurableUnitOfWork.Params> implements CommandReadyToExecute {

        private final ImmutableWorkspaceProvider workspace;
        private final Class<? extends ConfigurableUnitOfWork<T>> unitOfWorkClass;
        private final Action<? super T> spec;

        public DefaultCommandReadyToExecute(ImmutableWorkspaceProvider workspace, Class<? extends ConfigurableUnitOfWork<T>> unitOfWorkClass, Action<? super T> spec) {
            this.workspace = workspace;
            this.unitOfWorkClass = unitOfWorkClass;
            this.spec = spec;
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        @Override
        public CachingResult executeNow() {
            DefaultServiceRegistry services = new DefaultServiceRegistry(serviceRegistry);
            services.add(ImmutableWorkspaceProvider.class, workspace);
            InstanceGenerator generator = instantiatorFactory.decorate(services);
            Class paramClazz = (Class<?>) ((ParameterizedType) unitOfWorkClass.getGenericInterfaces()[0]).getActualTypeArguments()[0];
            Object params = generator.newInstance(paramClazz);
            services.add(paramClazz, params);
            spec.execute(Cast.uncheckedCast(params));
            ConfigurableUnitOfWork<T> workUnit = generator.newInstance(unitOfWorkClass);
            return delegate.execute(workUnit, null);
        }
    }

}
