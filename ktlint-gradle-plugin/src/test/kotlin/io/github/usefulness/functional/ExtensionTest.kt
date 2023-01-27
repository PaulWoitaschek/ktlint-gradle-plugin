package io.github.usefulness.functional

import org.gradle.testkit.runner.TaskOutcome
import io.github.usefulness.functional.utils.kotlinClass
import io.github.usefulness.functional.utils.resolve
import io.github.usefulness.functional.utils.settingsFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File

internal class ExtensionTest : WithGradleTest.Kotlin() {

    lateinit var projectRoot: File

    @BeforeEach
    fun setUp() {
        projectRoot = testProjectDir.apply {
            resolve("settings.gradle") { writeText(settingsFile) }
            resolve("build.gradle") {
                // language=groovy
                val buildScript =
                    """
                    plugins {
                        id 'kotlin'
                        id 'io.github.usefulness.ktlint-gradle-plugin'
                    }
                    
                    repositories {
                        mavenCentral()
                    }
                    
                    """.trimIndent()
                writeText(buildScript)
            }
        }
    }

    @Test
    fun `extension configures ignoreFailures`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                ktlint {
                    ignoreFailures = true
                }
                """.trimIndent()
            appendText(script)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }

        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
        }
    }

    @Test
    fun `extension configures reporters`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                ktlint {
                    reporters = ['html'] 
                }
                """.trimIndent()
            appendText(script)
        }
        projectRoot.resolve("src/main/kotlin/KotlinClass.kt") {
            writeText(kotlinClass("KotlinClass"))
        }

        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
        }
        val report = projectRoot.resolve("build/reports/ktlint/main-lint.html")
        assertTrue(report.readText().isNotEmpty())
    }

    @Test
    fun `extension configures disabledRules`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val script =
                """
                ktlint {
                    experimentalRules = true
                    disabledRules = ["filename", "experimental:unnecessary-parentheses-before-trailing-lambda"]
                }
                """.trimIndent()
            appendText(script)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }
        projectRoot.resolve("src/main/kotlin/UnnecessaryParentheses.kt") {
            writeText(
                """
                val FAILING = "should not have '()'".count() { it == 'x' }

                """.trimIndent(),
            )
        }

        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
        }
    }

    @Test
    fun `extension properties are evaluated only during task execution`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                plugins {
                    id 'kotlin'
                    id 'io.github.usefulness.ktlint-gradle-plugin'
                }
                
                repositories {
                    mavenCentral()
                }
                
                tasks.whenTaskAdded {
                    // configure all tasks eagerly
                }
                
                ktlint {
                    disabledRules = ["filename"]
                }
                
                """.trimIndent()
            writeText(buildScript)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("DifferentClassName"))
        }

        build("lintKotlin").apply {
            assertEquals(TaskOutcome.SUCCESS, task(":lintKotlinMain")?.outcome)
        }
    }

    @Test
    fun `can override ktlint version`() {
        projectRoot.resolve("build.gradle") {
            // language=groovy
            val buildScript =
                """
                plugins {
                    id 'kotlin'
                    id 'io.github.usefulness.ktlint-gradle-plugin'
                }
                
                repositories {
                    mavenCentral()
                }
                
                ktlint {
                    ktlintVersion = "0.46.0"
                }
                
                """.trimIndent()
            writeText(buildScript)
        }
        projectRoot.resolve("src/main/kotlin/FileName.kt") {
            writeText(kotlinClass("FileName"))
        }

        buildAndFail("lintKotlin").apply {
            assertEquals(TaskOutcome.FAILED, task(":lintKotlinMain")?.outcome) { "should fail due to incompatibility" }
            val expectedMessage = "Caused by: java.lang.NoSuchMethodError: 'com.pinterest.ktlint.core.api.EditorConfigOverride"
            assertTrue(output.contains(expectedMessage)) { "should explain the incompatibility" }
        }
        // remove `--configuration-cache-problems=warn` when upgrading to Gradle 7.6 https://github.com/gradle/gradle/issues/17470
        build("dependencies", "--configuration", "ktlint", "--configuration-cache-problems=warn").apply {
            assertTrue(output.contains("com.pinterest:ktlint:0.46.0")) {
                "should include overridden ktlin version in `ktlint` configuration"
            }
        }
    }
}
