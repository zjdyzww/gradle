package configurations

import common.Os
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildSteps
import model.CIBuildModel
import model.Stage
import model.TestCoverage
import model.TestType

class FunctionalTest(
    model: CIBuildModel,
    id: String,
    name: String,
    description: String,
    val testCoverage: TestCoverage,
    stage: Stage,
    subprojects: List<String> = listOf(),
    extraParameters: String = "",
    extraBuildSteps: BuildSteps.() -> Unit = {},
    preBuildSteps: BuildSteps.() -> Unit = {}
) : BaseGradleBuildType(model, stage = stage, init = {
    this.name = name
    this.description = description
    this.id(id)
    val testTaskName = "${testCoverage.testType.name}Test -x distributions-integ-tests:quickTest"
    val quickTest = testCoverage.testType == TestType.quick
    val buildScanTags = listOf("FunctionalTest")
    val buildScanValues = mapOf(
        "coverageOs" to testCoverage.os.name.toLowerCase(),
        "coverageJvmVendor" to testCoverage.vendor.name,
        "coverageJvmVersion" to testCoverage.testJvmVersion.name
    )

    if (name.contains("(configuration-cache)")) {
        requirements {
            doesNotContain("teamcity.agent.name", "ec2")
            // US region agents have name "EC2-XXX"
            doesNotContain("teamcity.agent.name", "EC2")
        }
    }

    applyTestDefaults(model, this, testTaskName, notQuick = !quickTest, os = testCoverage.os,
        extraParameters = (
            listOf(
                "-PtestJavaVersion=${testCoverage.testJvmVersion.major}",
                "-PtestJavaVendor=${testCoverage.vendor.name}") +
                buildScanTags.map { buildScanTag(it) } +
                buildScanValues.map { buildScanCustomValue(it.key, it.value) } +
                "-DenableTestDistribution=true" +
                extraParameters
            ).filter { it.isNotBlank() }.joinToString(separator = " "),
        timeout = testCoverage.testType.timeout,
        extraSteps = extraBuildSteps,
        preSteps = preBuildSteps)

    params {
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%e.grdev.net.access.key%")

        param("env.JAVA_HOME", "%${testCoverage.os.name.toLowerCase()}.${testCoverage.buildJvmVersion}.openjdk.64bit%")
        param("env.ANDROID_HOME", testCoverage.os.androidHome)
        param("env.ANDROID_SDK_ROOT", testCoverage.os.androidHome)
        if (testCoverage.os == Os.MACOS) {
            // Use fewer parallel forks on macOs, since the agents are not very powerful.
            param("maxParallelForks", "2")
        }

        if (testCoverage.testDistribution) {
            param("maxParallelForks", "16")
        }
    }
})
