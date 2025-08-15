plugins {
    `java-gradle-plugin`
    `maven-publish`
    kotlin("jvm") version "1.9.10"
}

group = "com.example"
version = "1.3.0"

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(gradleApi())
    
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation(gradleTestKit())
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("catalogPlugin") {
            id = "com.example.catalog-plugin"
            implementationClass = "com.example.CatalogSettingsPlugin"
            displayName = "Library Versions Catalog Plugin"
            description = "Creates and manages libs.versions.toml during settings evaluation phase"
        }
        create("catalogProjectPlugin") {
            id = "com.example.catalog-project-plugin"
            implementationClass = "com.example.CatalogProjectPlugin"
            displayName = "Catalog Project Plugin"
            description = "Simple project plugin that logs when applied"
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    jvmToolchain(11)
}