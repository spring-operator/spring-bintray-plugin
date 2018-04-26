package io.spring.gradle.bintray

import io.spring.gradle.bintray.task.CreatePackageTask
import io.spring.gradle.bintray.task.CreateVersionTask
import io.spring.gradle.bintray.task.MavenCentralSyncTask
import io.spring.gradle.bintray.task.PublishTask
import io.spring.gradle.bintray.task.SignTask
import io.spring.gradle.bintray.task.UploadTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication

/**
 * @author Jon Schneider
 */
class SpringBintrayPlugin : Plugin<Project> {
	lateinit var ext: SpringBintrayExtension

	override fun apply(project: Project) {
		ext = project.extensions.create("bintray", SpringBintrayExtension::class.java)

		val createPackageTask = project.tasks.create("bintrayCreatePackage", CreatePackageTask::class.java)

		val createVersionTask = project.tasks.create("bintrayCreateVersion", CreateVersionTask::class.java)
		createVersionTask.dependsOn(createPackageTask)

		project.tasks.withType(CreateVersionTask::class.java) { t ->
			val publication = project.extensions.getByType(PublishingExtension::class.java).publications.findByName(ext.publication)
			if (publication is MavenPublication) {
				publication.artifacts.forEach { artifact ->
					t.dependsOn(artifact.buildDependencies)
				}
			}
		}

		val uploadTask = project.tasks.create("bintrayUpload", UploadTask::class.java)
		uploadTask.dependsOn(createVersionTask)

		project.tasks.withType(UploadTask::class.java) { t ->
			val publication = project.extensions.getByType(PublishingExtension::class.java).publications.findByName(ext.publication)
			if (publication is MavenPublication) {
				t.dependsOn("generatePomFileFor${publication.name.capitalize()}Publication")
			}
		}

		// We try to sign on upload, so this task won't be part of the default chain of events.
		// It's here in case you need to sign manually after uploading for whatever reason.
		val signTask = project.tasks.create("bintraySign", SignTask::class.java)
		signTask.dependsOn(uploadTask)

		val publishTask = project.tasks.create("bintrayPublish", PublishTask::class.java)
		publishTask.dependsOn(uploadTask)

		val mavenCentralSyncTask = project.tasks.create("mavenCentralSync", MavenCentralSyncTask::class.java)
		mavenCentralSyncTask.dependsOn(publishTask)

		project.afterEvaluate {
			if (ext.bintrayUser == null || ext.bintrayKey == null || ext.repo == null || ext.publication == null || ext.licenses == null) {
				listOf(createPackageTask, createVersionTask, uploadTask, signTask, publishTask, mavenCentralSyncTask).forEach {
					it.onlyIf {
						project.logger.info("bintray.[bintrayUser, bintrayKey, repo, packageName, licenses] are all required")
						false
					}
				}
			}
		}
	}
}
