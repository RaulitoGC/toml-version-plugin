package com.example

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.InvalidUserDataException
import java.io.File

class CatalogSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val rootDir = settings.rootDir
        val gradleDir = File(rootDir, "gradle")
        val tomlFile = File(gradleDir, "libs.versions.toml")
        
        // Ensure gradle directory exists
        if (!gradleDir.exists()) {
            gradleDir.mkdirs()
            settings.gradle.rootProject { project ->
                project.logger.info("Created gradle directory: ${gradleDir.absolutePath}")
            }
        }
        
        // Get the bundled TOML file from plugin resources
        val resourceStream = this::class.java.classLoader.getResourceAsStream("libs.versions.toml")
        
        if (resourceStream == null) {
            throw IllegalStateException("Could not find bundled libs.versions.toml in plugin resources")
        }
        
        try {
            // Get template content as string
            val templateContent = resourceStream.use { input ->
                input.reader().readText()
            }
            
            if (!tomlFile.exists()) {
                // Create new file from template
                settings.gradle.rootProject { project ->
                    project.logger.lifecycle("⚠️  libs.versions.toml not found in gradle directory")
                    project.logger.lifecycle("📋 Creating libs.versions.toml from plugin template...")
                }
                
                tomlFile.writeText(templateContent)
                
                settings.gradle.rootProject { project ->
                    project.logger.lifecycle("✅ Successfully created libs.versions.toml at: ${tomlFile.absolutePath}")
                }
            } else {
                // For existing files, perform text-based merging to handle version.ref syntax
                settings.gradle.rootProject { project ->
                    project.logger.lifecycle("🔄 Updating libs.versions.toml with latest versions from plugin...")
                }
                
                val existingContent = tomlFile.readText()
                val mergedContent = mergeTomlAsText(existingContent, templateContent)
                
                tomlFile.writeText(mergedContent)
                
                settings.gradle.rootProject { project ->
                    project.logger.lifecycle("✅ Successfully updated libs.versions.toml with latest versions")
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Failed to create/update version catalog: ${e.message}", e)
        }
        
        // Configure the version catalog to use the TOML file
        // Gradle 7.4+ automatically creates a 'libs' catalog from gradle/libs.versions.toml if it exists
        // So we don't need to explicitly configure it when the file is in the standard location
        
        settings.gradle.rootProject { project ->
            project.logger.info("Catalog Settings Plugin applied. Version catalog is ready for use.")
        }
    }
    
    private fun mergeTomlAsText(existingContent: String, templateContent: String): String {
        // Parse sections from both files using regex patterns
        val existingVersions = extractVersionsSection(existingContent)
        val templateVersions = extractVersionsSection(templateContent)
        val existingLibraries = extractLibrariesSection(existingContent)
        val templateLibraries = extractLibrariesSection(templateContent)
        val existingPlugins = extractPluginsSection(existingContent)
        val templatePlugins = extractPluginsSection(templateContent)
        
        // Merge versions: template takes priority, but keep custom versions
        val mergedVersions = mergeVersionMaps(existingVersions, templateVersions)
        
        // For libraries and plugins, we'll be more conservative and just append custom ones
        val mergedLibraries = mergeLibraryMaps(existingLibraries, templateLibraries)
        val mergedPlugins = mergePluginMaps(existingPlugins, templatePlugins)
        
        // Build the merged TOML content
        return buildMergedToml(mergedVersions, mergedLibraries, mergedPlugins)
    }
    
    private fun extractVersionsSection(content: String): Map<String, String> {
        return extractSectionKeyValues(content, "versions")
    }
    
    private fun extractLibrariesSection(content: String): Map<String, String> {
        return extractSectionKeyValues(content, "libraries")
    }
    
    private fun extractPluginsSection(content: String): Map<String, String> {
        return extractSectionKeyValues(content, "plugins")
    }
    
    private fun extractSectionKeyValues(tomlContent: String, sectionName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = tomlContent.lines()
        var inTargetSection = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            when {
                // Found target section
                trimmedLine == "[$sectionName]" -> inTargetSection = true
                
                // Found different section - exit target section
                trimmedLine.startsWith("[") && trimmedLine != "[$sectionName]" -> inTargetSection = false
                
                // Parse key-value in target section
                inTargetSection && isKeyValueLine(trimmedLine) -> {
                    parseKeyValue(trimmedLine)?.let { (key, value) ->
                        result[key] = value
                    }
                }
            }
        }
        
        return result
    }
    
    private fun isKeyValueLine(line: String): Boolean {
        return line.isNotEmpty() && 
               !line.startsWith("#") && 
               line.contains("=")
    }
    
    private fun parseKeyValue(line: String): Pair<String, String>? {
        val equalIndex = line.indexOf("=")
        if (equalIndex == -1) return null
        
        val key = line.substring(0, equalIndex).trim()
        val valueRaw = line.substring(equalIndex + 1).trim()
        
        // Remove quotes and inline comments
        val value = cleanValue(valueRaw)
        
        return if (key.isNotEmpty()) key to value else null
    }
    
    private fun cleanValue(rawValue: String): String {
        var value = rawValue
        
        // Remove inline comments
        val commentIndex = value.indexOf("#")
        if (commentIndex != -1) {
            value = value.substring(0, commentIndex).trim()
        }
        
        // Remove surrounding quotes
        if (value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length - 1)
        }
        
        return value
    }
    
    private fun mergeVersionMaps(existing: Map<String, String>, template: Map<String, String>): Map<String, String> {
        val result = template.toMutableMap() // Template takes priority
        // Add custom versions that aren't in template
        existing.forEach { (key, value) ->
            if (!template.containsKey(key)) {
                result[key] = value
            }
        }
        return result
    }
    
    private fun mergeLibraryMaps(existing: Map<String, String>, template: Map<String, String>): Map<String, String> {
        val result = template.toMutableMap() // Template takes priority
        // Add custom libraries that aren't in template
        existing.forEach { (key, value) ->
            if (!template.containsKey(key)) {
                result[key] = value
            }
        }
        return result
    }
    
    private fun mergePluginMaps(existing: Map<String, String>, template: Map<String, String>): Map<String, String> {
        val result = template.toMutableMap() // Template takes priority
        // Add custom plugins that aren't in template
        existing.forEach { (key, value) ->
            if (!template.containsKey(key)) {
                result[key] = value
            }
        }
        return result
    }
    
    private fun buildMergedToml(versions: Map<String, String>, libraries: Map<String, String>, plugins: Map<String, String>): String {
        val builder = StringBuilder()
        
        // Add header comment
        builder.appendLine("# Common Android Library Versions")
        builder.appendLine("# This file is bundled with the catalog plugin and provides")
        builder.appendLine("# curated versions of popular Android libraries")
        builder.appendLine()
        
        // Add versions section
        builder.appendLine("[versions]")
        builder.appendLine("# Core Android")
        versions.forEach { (key, value) ->
            builder.appendLine("""$key = "$value"""")
        }
        builder.appendLine()
        
        // Add libraries section
        builder.appendLine("[libraries]")
        builder.appendLine("# Core Android")
        libraries.forEach { (key, value) ->
            builder.appendLine("$key = $value")
        }
        builder.appendLine()
        
        // Add plugins section
        builder.appendLine("[plugins]")
        plugins.forEach { (key, value) ->
            builder.appendLine("$key = $value")
        }
        
        return builder.toString()
    }
}