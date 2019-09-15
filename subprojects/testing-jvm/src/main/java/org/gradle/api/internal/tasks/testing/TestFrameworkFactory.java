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

import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;

public class TestFrameworkFactory {
    private final ObjectFactory objectFactory;

    public TestFrameworkFactory(ObjectFactory objectFactory) {
        this.objectFactory = objectFactory;
    }

    public JUnitTestFramework createJUnit(Test test) {
        return createTestFramework(JUnitTestFramework.class, test, test.getFilter());
    }

    public TestNGTestFramework createTestNG(Test test) {
        return createTestFramework(TestNGTestFramework.class, test, test.getFilter());
    }

    public JUnitPlatformTestFramework createJUnitPlatform(Test test) {
        return createTestFramework(JUnitPlatformTestFramework.class, test.getFilter());
    }

    private <T extends TestFramework> T createTestFramework(Class<T> clazz, Object... params) {
        return objectFactory.newInstance(clazz, params);
    }
}
