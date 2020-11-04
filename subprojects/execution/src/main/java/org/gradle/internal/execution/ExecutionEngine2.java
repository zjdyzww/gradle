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
package org.gradle.internal.execution;

import org.gradle.api.Action;
import org.gradle.internal.execution.history.ExecutionHistoryStore;
import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;

import javax.inject.Inject;
import java.util.Optional;

public interface ExecutionEngine2 {
    CommandBuilder prepare();

    interface CommandBuilder {
        CommandInWorkspace workspace(ImmutableWorkspaceProvider workspace);
    }

    interface CommandInWorkspace {
        <T extends ConfigurableUnitOfWork.Params> CommandReadyToExecute workUnit(Class<? extends ConfigurableUnitOfWork<T>> unitOfWorkClass, Action<? super T> spec);
    }

    interface CommandReadyToExecute {
        CachingResult executeNow();
    }

    interface ConfigurableUnitOfWork<T extends ConfigurableUnitOfWork.Params> extends UnitOfWork {
        interface Params {}

        @Inject
        T getParams();

        @Inject
        ImmutableWorkspaceProvider getWorkspace();

        @Override
        default <U> U withWorkspace(String identity, WorkspaceAction<U> action) {
            return getWorkspace().withWorkspace(identity, (ws, history) -> action.executeInWorkspace(ws));
        }

        @Override
        default Optional<ExecutionHistoryStore> getHistory() {
            return Optional.of(getWorkspace().getHistory());
        }
    }
}
