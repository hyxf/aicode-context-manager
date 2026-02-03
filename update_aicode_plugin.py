import os
import sys

# Define base paths
BASE_DIR = os.getcwd()
PROJECT_DIR = os.path.join(BASE_DIR)

def update_file(file_path, content):
    """Writes content to a file."""
    full_path = os.path.join(PROJECT_DIR, file_path)
    try:
        with open(full_path, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"✅ Successfully updated: {file_path}")
    except Exception as e:
        print(f"❌ Error updating {file_path}: {e}")
        sys.exit(1)

# -----------------------------------------------------------------------------
# Update build.gradle.kts
# Requirement: Increase untilBuild to support newer IDE versions (253.*)
# -----------------------------------------------------------------------------
build_gradle_content = r"""plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.2"
}

group = "com.aicode"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

intellij {
    version.set("2023.2")
    type.set("IC")
    plugins.set(listOf("java"))
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("232")
        // Updated to support newer IDE versions (e.g. 2025.x)
        untilBuild.set("300.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
"""

# -----------------------------------------------------------------------------
# Execution
# -----------------------------------------------------------------------------
print("🚀 Starting Compatibility Fix...")

# Ensure directory exists
if not os.path.exists(PROJECT_DIR):
    print(f"❌ Could not find project directory: {PROJECT_DIR}")
    print("Please make sure you run this script from the project root.")
    sys.exit(1)

update_file("build.gradle.kts", build_gradle_content)

print("✨ Compatibility fixed! Please rebuild the plugin (Gradle Clean & Build).")