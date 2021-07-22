/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.watch

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.WINDOWS)
class UnsupportedFileSystemsWatchingIntegrationTest extends AbstractFileSystemWatchingIntegrationTest {

    @Rule
    public final TestNameTestDirectoryProvider temporaryFolderOnFat = new TestNameTestDirectoryProvider(new TestFile(
        "D:\\tmp\\test files"
    ), getClass())

    def "does not watch unsupported file systems"() {
        def projectDir = temporaryFolderOnFat.createDir("project")
        projectDir.file("build.gradle") << """
            apply plugin: "java-library"
        """
        projectDir.file("settings.gradle").createFile()

        file("src/main/java/MyClass.java") << "public class MyClass {}"

        executer.inDirectory(projectDir)

        when:
        succeeds("assemble", "--info")
        then:
        outputContains("Now considering [] as hierarchies to watch")
    }
}
