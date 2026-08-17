package com.aicode.feature.gradle.service

import com.aicode.feature.gradle.model.ExportOptions
import java.nio.file.Path

/** Builds the short-lived Gradle-side task. It uses public resolution APIs, never cache paths. */
object GradleExportInitScript {
    const val TASK_NAME = "aicodeExportResolvedDependencies"

    fun create(options: ExportOptions, resultFile: Path): String {
        val target = quote(options.repository.toAbsolutePath().normalize().toString())
        val result = quote(resultFile.toAbsolutePath().normalize().toString())
        val scope = options.scope.name
        return """
            import groovy.json.JsonOutput
            import java.nio.file.Files
            import java.nio.file.StandardCopyOption
            import java.security.MessageDigest
            import org.gradle.api.artifacts.component.ModuleComponentIdentifier
            import org.gradle.api.artifacts.component.ProjectComponentIdentifier
            import org.gradle.jvm.JvmLibrary
            import org.gradle.language.base.artifact.SourcesArtifact
            import org.gradle.language.java.artifact.JavadocArtifact
            import org.gradle.maven.MavenModule
            import org.gradle.maven.MavenPomArtifact

            def aicodeTarget = new File('$target')
            def aicodeResult = new File('$result')
            def aicodeScope = '$scope'
            def aicodeIncludeTests = ${options.includeTests}
            def aicodeIncludeBuildscript = ${options.includeBuildscriptClasspath}

            def aicodeSha256 = { File file ->
                def digest = MessageDigest.getInstance('SHA-256')
                file.withInputStream { stream ->
                    byte[] buffer = new byte[8192]
                    int read
                    while ((read = stream.read(buffer)) >= 0) digest.update(buffer, 0, read)
                }
                digest.digest().encodeHex().toString()
            }
            def aicodeCopy = { File source, File destination ->
                destination.parentFile.mkdirs()
                if (destination.exists()) {
                    if (destination.length() == source.length() && aicodeSha256(destination) == aicodeSha256(source)) return 'skipped'
                    throw new GradleException("Existing file has different content: " + destination)
                }
                def temporary = File.createTempFile(destination.name, '.part', destination.parentFile)
                try {
                    Files.copy(source.toPath(), temporary.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    try {
                        Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary.toPath(), destination.toPath())
                    }
                } finally {
                    temporary.delete()
                }
                'exported'
            }
            def aicodeConfigurationSelected = { configuration ->
                if (!configuration.canBeResolved) return false
                def name = configuration.name.toLowerCase(Locale.ROOT)
                if (!aicodeIncludeTests && (name.contains('test') || name.contains('androidtest'))) return false
                if (aicodeScope == 'ALL') return true
                def runtime = name == 'runtimeclasspath' || name.endsWith('runtimeclasspath')
                if (aicodeScope == 'RUNTIME') return runtime
                def compile = name == 'compileclasspath' || name.endsWith('compileclasspath')
                runtime || compile
            }
            def aicodeDestination = { ModuleComponentIdentifier id, File source ->
                def directory = new File(aicodeTarget, id.group.replace('.', '/') + '/' + id.module + '/' + id.version)
                new File(directory, source.name)
            }

            gradle.projectsEvaluated {
                rootProject.tasks.register('${TASK_NAME}') {
                    group = 'AICode'
                    description = 'Exports resolved external dependencies to a Maven repository'
                    doLast {
                        long started = System.currentTimeMillis()
                        def components = new LinkedHashMap()
                        def failures = new LinkedHashSet()
                        def warnings = new LinkedHashSet()
                        int exported = 0
                        int skipped = 0
                        def aicodeWriteResult = { boolean cancelled ->
                            def data = [totalDependencies: components.size(), exportedFiles: exported,
                                skippedFiles: skipped, failedDependencies: failures as List,
                                warnings: warnings as List, cancelled: cancelled,
                                durationMillis: System.currentTimeMillis() - started]
                            aicodeResult.parentFile.mkdirs()
                            aicodeResult.text = JsonOutput.toJson(data)
                        }
                        try {
                            def aicodeCollectConfiguration = { candidateProject, configuration ->
                                    try {
                                        def collection = configuration.incoming.artifactView { view -> view.lenient(true) }.artifacts
                                        collection.failures.each { failures.add(candidateProject.path + ':' + configuration.name + ': ' + it.message) }
                                        collection.artifacts.each { artifact ->
                                            def id = artifact.id.componentIdentifier
                                            if (id instanceof ModuleComponentIdentifier) {
                                                def key = id.group + ':' + id.module + ':' + id.version
                                                def entry = components.computeIfAbsent(key) { [id: id, project: candidateProject, files: new LinkedHashSet()] }
                                                entry.files.add(artifact.file)
                                            } else if (!(id instanceof ProjectComponentIdentifier)) {
                                                warnings.add('Skipped non-Maven component ' + id.displayName)
                                            }
                                        }
                                    } catch (Exception ex) {
                                        failures.add(candidateProject.path + ':' + configuration.name + ': ' + ex.message)
                                    }
                            }
                            rootProject.allprojects.each { candidateProject ->
                                candidateProject.configurations.findAll(aicodeConfigurationSelected).each { configuration ->
                                    aicodeCollectConfiguration(candidateProject, configuration)
                                }
                                if (aicodeIncludeBuildscript) {
                                    candidateProject.buildscript.configurations.findAll { it.canBeResolved && it.name == 'classpath' }.each { configuration ->
                                        aicodeCollectConfiguration(candidateProject, configuration)
                                    }
                                }
                            }
                            int total = components.size()
                            int current = 0
                            aicodeWriteResult(false)
                            components.each { coordinate, entry ->
                                current++
                                logger.lifecycle('AICODE_EXPORT_PROGRESS|' + current + '|' + total + '|' + coordinate)
                                entry.files.each { source ->
                                    try {
                                        def status = aicodeCopy(source, aicodeDestination(entry.id, source))
                                        if (status == 'exported') exported++ else skipped++
                                    } catch (Exception ex) {
                                        failures.add(coordinate + ': ' + ex.message)
                                    }
                                }
                                try {
                                    def query = entry.project.dependencies.createArtifactResolutionQuery()
                                        .forComponents(entry.id)
                                        .withArtifacts(MavenModule, MavenPomArtifact)
                                        .execute()
                                    query.resolvedComponents.each { component ->
                                        component.getArtifacts(MavenPomArtifact).each { artifact ->
                                            if (artifact instanceof org.gradle.api.artifacts.result.ResolvedArtifactResult) {
                                                def status = aicodeCopy(artifact.file, aicodeDestination(entry.id, artifact.file))
                                                if (status == 'exported') exported++ else skipped++
                                            } else warnings.add('POM unavailable for ' + coordinate)
                                        }
                                    }
                                    query.unresolvedComponents.each { warnings.add('POM unavailable for ' + coordinate + ': ' + it.failure.message) }
                                } catch (Exception ex) {
                                    warnings.add('POM unavailable for ' + coordinate + ': ' + ex.message)
                                }
                                [[JvmLibrary, SourcesArtifact], [JvmLibrary, JavadocArtifact]].each { types ->
                                    try {
                                        def query = entry.project.dependencies.createArtifactResolutionQuery()
                                            .forComponents(entry.id).withArtifacts(types[0], types[1]).execute()
                                        query.resolvedComponents.each { component ->
                                            component.getArtifacts(types[1]).each { artifact ->
                                                if (artifact instanceof org.gradle.api.artifacts.result.ResolvedArtifactResult) {
                                                    def status = aicodeCopy(artifact.file, aicodeDestination(entry.id, artifact.file))
                                                    if (status == 'exported') exported++ else skipped++
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {
                                        // Sources and Javadoc are optional and do not make the dependency unusable.
                                    }
                                }
                                warnings.add('Gradle Module Metadata is not exposed by the stable Gradle artifact query API for ' + coordinate)
                                if (entry.id.version.endsWith('-SNAPSHOT')) warnings.add('Snapshot repository metadata could not be exported reliably for ' + coordinate)
                                aicodeWriteResult(false)
                            }
                            logger.lifecycle('AICode dependency export finished: ' + components.size() + ' dependencies, ' + exported + ' exported, ' + skipped + ' skipped, ' + failures.size() + ' failed')
                        } catch (org.gradle.api.tasks.StopExecutionException ex) {
                            throw ex
                        }
                    }
                }
            }
        """.trimIndent()
    }

    private fun quote(value: String): String = value.replace("\\", "\\\\").replace("'", "\\'")
}
