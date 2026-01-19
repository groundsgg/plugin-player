package gg.grounds.config

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import org.slf4j.Logger
import tools.jackson.databind.DeserializationFeature
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.module.kotlin.KotlinModule
import tools.jackson.module.kotlin.readValue

class MessagesConfigLoader(private val logger: Logger, private val dataDirectory: Path) {
    private val mapper: YAMLMapper =
        YAMLMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build()

    fun loadOrCreate(): MessagesConfig {
        val yamlFile = dataDirectory.resolve("messages.yml")

        return try {
            Files.createDirectories(dataDirectory)

            if (Files.notExists(yamlFile)) {
                copyDefaultConfig(yamlFile)
            }

            Files.newInputStream(yamlFile).use { input -> parseYaml(input) }
        } catch (e: Exception) {
            logger.warn("Failed to load messages config (path={})", yamlFile, e)
            MessagesConfig()
        }
    }

    private fun parseYaml(input: InputStream): MessagesConfig {
        return try {
            mapper.readValue<MessagesConfig>(input)
        } catch (e: Exception) {
            logger.warn("Failed to parse messages config; using defaults (path=messages.yml)", e)
            MessagesConfig()
        }
    }

    private fun copyDefaultConfig(target: Path) {
        val resourceName = "messages.yml"
        val inputStream = javaClass.classLoader.getResourceAsStream(resourceName)
        if (inputStream == null) {
            logger.warn(
                "Default messages resource missing; writing defaults (resource={}, path={})",
                resourceName,
                target,
            )
            writeYaml(target, MessagesConfig())
            return
        }

        inputStream.use { input ->
            Files.newOutputStream(target).use { output -> input.copyTo(output) }
        }
    }

    private fun writeYaml(target: Path, config: MessagesConfig) {
        Files.newOutputStream(target).use { output ->
            output.writer(Charsets.UTF_8).use { writer -> mapper.writeValue(writer, config) }
        }
    }
}
