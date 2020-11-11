package projects

import common.failedTestArtifactDestination
import DistributedTest.vcsRoots.DistributedTest_DistributedTest
import configurations.StagePasses
import jetbrains.buildServer.configs.kotlin.v2019_2.AbsoluteId
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.projectFeatures.VersionedSettings
import jetbrains.buildServer.configs.kotlin.v2019_2.projectFeatures.versionedSettings
import model.CIBuildModel
import model.Stage

class RootProject(model: CIBuildModel) : Project({
    uuid = model.projectPrefix.removeSuffix("_")
    id = AbsoluteId(uuid)
    parentId("_Root")
    name = model.rootProjectName

    vcsRoot(DistributedTest_DistributedTest)

    features {
        versionedSettings {
            id = "PROJECT_EXT_39"
            mode = VersionedSettings.Mode.ENABLED
            buildSettingsMode = VersionedSettings.BuildSettingsMode.USE_CURRENT_SETTINGS
            rootExtId = "${DistributedTest_DistributedTest.id}"
            showChanges = false
            settingsFormat = VersionedSettings.Format.KOTLIN
            storeSecureParamsOutsideOfVcs = true
        }
    }

    params {
        param("env.GRADLE_ENTERPRISE_ACCESS_KEY", "%e.grdev.net.access.key%")
    }

    var prevStage: Stage? = null
    model.stages.forEach { stage ->
        val stageProject = StageProject(model, stage, uuid)
        val stagePasses = StagePasses(model, stage, prevStage, stageProject)
        buildType(stagePasses)
        subProject(stageProject)
        prevStage = stage
    }

    buildTypesOrder = buildTypes
    subProjectsOrder = subProjects

    cleanup {
        baseRule {
            history(days = 14)
        }
        baseRule {
            artifacts(days = 14, artifactPatterns = """
                +:**/*
                +:$failedTestArtifactDestination/**/*"
            """.trimIndent())
        }
    }
})
