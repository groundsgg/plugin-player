package gg.grounds.config

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger
import tools.jackson.databind.DeserializationFeature
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue

class PluginConfigLoader(private val logger: Logger, private val dataDirectory: Path) {
    private val mapper: YAMLMapper =
        YAMLMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    fun loadOrCreate(): PluginConfig {
        val yamlFile = dataDirectory.resolve("config.yml")

        return try {
            Files.createDirectories(dataDirectory)

            if (Files.notExists(yamlFile)) {
                copyDefaultConfig(yamlFile)
            }

            Files.newInputStream(yamlFile).use { input -> parseYaml(input) }
        } catch (e: Exception) {
            logger.warn("Unable to load config from {}", yamlFile, e)
            PluginConfig()
        }
    }

    private fun parseYaml(input: InputStream): PluginConfig {
        return try {
            mapper.readValue<PluginConfig>(input)
        } catch (e: Exception) {
            logger.warn("Unable to parse config.yml; using defaults", e)
            PluginConfig()
        }
    }

    private fun copyDefaultConfig(target: Path) {
        val resourceName = "config.yml"
        val inputStream = javaClass.classLoader.getResourceAsStream(resourceName)
        if (inputStream == null) {
            logger.warn(
                "Default config resource {} not found; writing generated defaults",
                resourceName,
            )
            writeYaml(target, PluginConfig())
            return
        }

        inputStream.use { input ->
            Files.newOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun writeYaml(target: Path, config: PluginConfig) {
        Files.newOutputStream(target).use { output ->
            output.writer(Charsets.UTF_8).use { writer -> mapper.writeValue(writer, config) }
        }
    }
}
