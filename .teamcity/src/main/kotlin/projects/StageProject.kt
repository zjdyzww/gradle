package projects

import common.failedTestArtifactDestination
import common.isLinuxBuild
import configurations.FunctionalTest
import configurations.PartialTrigger
import configurations.PerformanceTest
import configurations.PerformanceTestsPass
import configurations.SanityCheck
import configurations.SmokeTests
import configurations.buildReportTab
import jetbrains.buildServer.configs.kotlin.v2019_2.BuildType
import jetbrains.buildServer.configs.kotlin.v2019_2.FailureAction
import jetbrains.buildServer.configs.kotlin.v2019_2.IdOwner
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.RelativeId
import model.CIBuildModel
import model.FlameGraphGeneration
import model.PerformanceTestBucketProvider
import model.PerformanceTestCoverage
import model.SpecificBuild
import model.Stage
import model.StageNames
import model.TestType

class StageProject(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage) : Project({
    this.id("${model.projectId}_Stage_${stage.stageName.id}")
    this.name = stage.stageName.stageName
    this.description = stage.stageName.description
}) {
    val specificBuildTypes: List<BuildType>

    val performanceTests: List<PerformanceTestsPass>

    val functionalTests: List<FunctionalTest>

    init {
        features {
            if (stage.specificBuilds.contains(SpecificBuild.SanityCheck)) {
                buildReportTab("API Compatibility Report", "$failedTestArtifactDestination/report-architecture-test-binary-compatibility-report.html")
                buildReportTab("Incubating APIs Report", "incubation-reports/all-incubating.html")
            }
            if (stage.performanceTests.isNotEmpty()) {
                buildReportTab("Performance", "performance-test-results.zip!report/index.html")
            }
        }

        specificBuildTypes = stage.specificBuilds.map {
            it.create(model, stage)
        }
        specificBuildTypes.forEach(this::buildType)

        performanceTests = stage.performanceTests.map { createPerformanceTests(model, performanceTestBucketProvider, stage, it) } +
            stage.flameGraphs.map { createFlameGraphs(model, stage, it) }

        val (topLevelCoverage, allCoverage) = stage.functionalTests.partition { it.testType == TestType.soak || it.testDistribution }
        val topLevelFunctionalTests = topLevelCoverage
            .map { FunctionalTest(model, it.asConfigurationId(model), it.asName(), it.asName(), it, stage = stage) }
        topLevelFunctionalTests.forEach(this::buildType)

        val coverageFunctionalTests = allCoverage
            .map { testCoverage ->
                val coverageFunctionalTest = FunctionalTest(
                    model,
                    testCoverage.asId(model),
                    testCoverage.asName(),
                    testCoverage.asName(),
                    testCoverage,
                    stage)

                if (stage.functionalTestsDependOnSpecificBuilds) {
                    specificBuildTypes.forEach(coverageFunctionalTest::dependsOn)
                }
                if (!(stage.functionalTestsDependOnSpecificBuilds && stage.specificBuilds.contains(SpecificBuild.SanityCheck)) && stage.dependsOnSanityCheck) {
                    coverageFunctionalTest.dependsOn(RelativeId(SanityCheck.buildTypeId(model)))
                }
                coverageFunctionalTest
            }

        functionalTests = topLevelFunctionalTests + coverageFunctionalTests
        functionalTests.forEach(this::buildType)

        if (stage.stageName !in listOf(StageNames.QUICK_FEEDBACK_LINUX_ONLY, StageNames.QUICK_FEEDBACK)) {
            if (topLevelFunctionalTests.size + functionalTests.size > 1) {
                buildType(PartialTrigger("All Functional Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_FuncTests", model, functionalTests))
            }
            val smokeTests = specificBuildTypes.filterIsInstance<SmokeTests>()
            if (smokeTests.size > 1) {
                buildType(PartialTrigger("All Smoke Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_SmokeTests", model, smokeTests))
            }
            val crossVersionTests = functionalTests.filter { it.testCoverage.testType in setOf(TestType.allVersionsCrossVersion, TestType.quickFeedbackCrossVersion) }
            if (crossVersionTests.size > 1) {
                buildType(PartialTrigger("All Cross-Version Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_CrossVersionTests", model, crossVersionTests))
            }
            if (specificBuildTypes.size > 1) {
                buildType(PartialTrigger("All Specific Builds for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_SpecificBuilds", model, specificBuildTypes))
            }
            if (performanceTests.size > 1) {
                buildType(PartialTrigger("All Performance Tests for ${stage.stageName.stageName}", "Stage_${stage.stageName.id}_PerformanceTests", model, performanceTests))
            }
        }
    }

    private
    fun createPerformanceTests(model: CIBuildModel, performanceTestBucketProvider: PerformanceTestBucketProvider, stage: Stage, performanceTestCoverage: PerformanceTestCoverage): PerformanceTestsPass {
        val performanceTestProject = AutomaticallySplitPerformanceTestProject(model, performanceTestBucketProvider, stage, performanceTestCoverage)
        subProject(performanceTestProject)
        return PerformanceTestsPass(model, performanceTestProject).also(this::buildType)
    }

    private fun createFlameGraphs(model: CIBuildModel, stage: Stage, flameGraphSpec: FlameGraphGeneration): PerformanceTestsPass {
        val flameGraphBuilds = flameGraphSpec.buildSpecs.mapIndexed { index, buildSpec ->
            createFlameGraphBuild(model, stage, buildSpec, index)
        }
        val performanceTestProject = ManuallySplitPerformanceTestProject(model, flameGraphSpec, flameGraphBuilds)
        subProject(performanceTestProject)
        return PerformanceTestsPass(model, performanceTestProject).also(this::buildType)
    }

    private
    fun createFlameGraphBuild(model: CIBuildModel, stage: Stage, flameGraphGenerationBuildSpec: FlameGraphGeneration.FlameGraphGenerationBuildSpec, bucketIndex: Int): PerformanceTest = flameGraphGenerationBuildSpec.run {
        PerformanceTest(
            model,
            stage,
            flameGraphGenerationBuildSpec,
            description = "Flame graphs with $profiler for ${performanceScenario.scenario.scenario} | ${performanceScenario.testProject} on ${os.asName()} (bucket $bucketIndex)",
            performanceSubProject = "performance",
            bucketIndex = bucketIndex,
            extraParameters = "--profiler $profiler --tests \"${performanceScenario.scenario.className}.${performanceScenario.scenario.scenario}\"",
            testProjects = listOf(performanceScenario.testProject),
            performanceTestTaskSuffix = "PerformanceAdHocTest"
        )
    }
}

private fun FunctionalTest.dependsOn(dependency: IdOwner) {
    if (this.isLinuxBuild()) {
        dependencies {
            dependency(dependency) {
                snapshot {
                    onDependencyFailure = FailureAction.CANCEL
                    onDependencyCancel = FailureAction.CANCEL
                }
            }
        }
    }
}
