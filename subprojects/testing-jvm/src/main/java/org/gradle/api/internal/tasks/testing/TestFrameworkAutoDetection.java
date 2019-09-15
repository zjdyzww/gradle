/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.tasks.testing.Test;

public class TestFrameworkAutoDetection {

    private final TestFrameworkFactory testFrameworkFactory;

    public TestFrameworkAutoDetection(TestFrameworkFactory testFrameworkFactory) {
        this.testFrameworkFactory = testFrameworkFactory;
    }

    public TestFramework assumeDefaultFor(Test test) {
        return testFrameworkFactory.createJUnit(test);
    }

    public TestFramework detect(Test test, Configuration runtime) {
        DependencySet runtimeDependencies = runtime.getDependencies();

        boolean foundJUnitPlatformEngine = false; // org.junit.jupiter:junit-jupiter
        boolean foundJUnit = false; // junit:junit
        boolean foundTestNG = false; // org.testng:testng

        for (Dependency dependency : runtimeDependencies) {
            if (matchesCoordinates("org.junit.jupiter", "junit-jupiter", dependency)) {
                foundJUnitPlatformEngine = true;
            }
            if (matchesCoordinates("junit", "junit", dependency)) {
                foundJUnit = true;
            }
            if (matchesCoordinates("org.testng", "testng", dependency)) {
                foundTestNG = true;
            }
        }

        if (foundJUnitPlatformEngine) {
            return testFrameworkFactory.createJUnitPlatform(test);
        } else if (foundJUnit) {
            return testFrameworkFactory.createJUnit(test);
        } else if (foundTestNG) {
            return testFrameworkFactory.createTestNG(test);
        }

        // Assuming whatever the default would be
        return assumeDefaultFor(test);
    }

    private boolean matchesCoordinates(String group, String name, Dependency dependency) {
        return (dependency.getGroup() != null && dependency.getGroup().equals(group)) && dependency.getName().equals(name);
    }
}
