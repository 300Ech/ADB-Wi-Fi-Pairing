import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.intellij.platform") // versión fijada en settings.gradle.kts
    alias(libs.plugins.changelog)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Igual que la plataforma: no generar métodos puente para defaults de interfaces.
        // Evita falsos usos de API deprecada/experimental en el Plugin Verifier.
        jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
    }
}

// Ruta opcional a un Android Studio instalado, leída de local.properties (no versionado).
// Evita descargar la IDE en cada máquina de desarrollo; CI siempre usa platformVersion.
val localPlatformPath: String? = rootProject.file("local.properties")
    .takeIf { it.isFile }
    ?.let { file -> Properties().apply { file.inputStream().use(::load) } }
    ?.getProperty("platform.localPath")
    ?.takeIf { it.isNotBlank() && file(it).exists() }

dependencies {
    implementation(libs.zxing.core)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.platformLauncher)

    // https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        if (localPlatformPath != null) {
            local(localPlatformPath)
        } else {
            androidStudio(providers.gradleProperty("platformVersion"))
        }
        bundledPlugin("org.jetbrains.android")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.JUnit5)
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // La descripción del Marketplace se toma del README, entre los marcadores
        // <!-- Plugin description --> y <!-- Plugin description end -->.
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("README.md debe contener los marcadores:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        // Las notas de versión salen del CHANGELOG.md.
        val changelog = project.changelog
        changeNotes = version.map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Una versión con sufijo (p. ej. 0.2.0-beta.1) se publica en ese canal del Marketplace.
        channels = providers.gradleProperty("pluginVersion").map {
            listOf(it.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    pluginVerification {
        ides {
            if (localPlatformPath != null) {
                local(localPlatformPath)
            } else {
                create(IntelliJPlatformType.AndroidStudio, providers.gradleProperty("platformVersion"))
            }
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
    versionPrefix = ""
}

kover {
    reports {
        total {
            xml {
                onCheck = true
            }
        }
    }
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(layout.projectDirectory.file("detekt/detekt.yml"))
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

// detekt 1.23.x está compilado con Kotlin 2.0.21 y falla si su classpath recibe otra versión.
configurations.matching { it.name == "detekt" }.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin") {
            useVersion(libs.versions.detektKotlin.get())
        }
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "21"
        reports {
            html.required = true
            sarif.required = true
            xml.required = false
            txt.required = false
        }
    }

    runIde {
        // Salta el asistente de primer arranque de Android Studio en el sandbox.
        jvmArgumentProviders += CommandLineArgumentProvider { listOf("-Ddisable.android.first.run=true") }
    }

    publishPlugin {
        dependsOn(patchChangelog)
    }
}
