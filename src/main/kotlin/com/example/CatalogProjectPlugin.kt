package com.example

import org.gradle.api.Plugin
import org.gradle.api.Project

class CatalogProjectPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.logger.lifecycle("🔧 Catalog Project Plugin applied to project: ${project.name}")
        
        // Load versions from libs.versions.toml
        loadVersionsFromToml(project)
        
        project.logger.lifecycle("Properties -> ${project.extensions.extraProperties}")
    }
    
    private fun loadVersionsFromToml(project: Project) {
        try {
            // Get the TOML file from resources
            val resourceStream = this::class.java.classLoader.getResourceAsStream("libs.versions.toml")
            project.logger.error("Testing libs.versions.toml loading")
            if (resourceStream == null) {
                project.logger.error("Could not find libs.versions.toml in plugin resources")
                return
            }
            project.logger.error("Testing libs.versions.toml loading 2")
            // Read and parse the TOML file content manually
            val templateContent = resourceStream.use { input ->
                input.reader().readText()
            }

            project.logger.error("Testing libs.versions.toml loading 3")
            val versions = extractVersionsSection(templateContent)
            project.logger.error("Loaded ${versions.size} versions from TOML")
            
            versions.forEach { (key, value) ->
                project.logger.error("Version: $key = $value")
            }

        } catch (e: Exception) {
            project.logger.error("Error reading libs.versions.toml: ${e.message}")
        }
    }
    
    private fun extractVersionsSection(tomlContent: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val lines = tomlContent.lines()
        var inVersionsSection = false
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            when {
                // Found versions section
                trimmedLine == "[versions]" -> inVersionsSection = true
                
                // Found different section - exit versions section
                trimmedLine.startsWith("[") && trimmedLine != "[versions]" -> inVersionsSection = false
                
                // Parse key-value in versions section
                inVersionsSection && isKeyValueLine(trimmedLine) -> {
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
}