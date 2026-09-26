import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.util.GradleVersion
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object ExternalModDownloader {

    private const val CONNECT_TIMEOUT = 10000
    private const val READ_TIMEOUT = 30000

    private val USER_AGENT = "Gradle/${GradleVersion.current().version}"

    fun download(
        project: Project,
        downloadUrl: String,
        outputDir: File,
        fileName: String? = null
    ): File? {
        val trimmedUrl = downloadUrl.trim()
        require(trimmedUrl.isNotBlank()) { "下载链接不能为空！" }
        require(outputDir.isDirectory || outputDir.mkdirs()) { "无法创建输出目录：${outputDir.absolutePath}" }
        return try {
            val targetFileName = fileName ?: extractFileNameFromUrl(trimmedUrl)
            ?: throw IOException("无法识别文件名，请手动指定 fileName 参数")
            val targetFile = outputDir.resolve(targetFileName)
            if (targetFile.exists() && targetFile.length() > 0) {

                return targetFile
            }
            project.logger.log(LogLevel.LIFECYCLE, "开始下载：$trimmedUrl")
            val connection = createConnection(trimmedUrl)
            connection.connect()
            downloadFile(connection, targetFile)
            if (!targetFile.exists() || targetFile.length() == 0L) {
                throw IOException("下载的文件为空或损坏")
            }
            project.logger.log(LogLevel.LIFECYCLE, "下载成功：${targetFile.absolutePath}")
            targetFile

        } catch (e: IllegalArgumentException) {
            project.logger.log(LogLevel.ERROR, "下载参数错误：${e.message}")
            null
        } catch (e: IOException) {
            project.logger.log(LogLevel.ERROR, "下载失败：${e.message}", e)
            null
        } catch (e: Exception) {
            project.logger.log(LogLevel.ERROR, "未知错误：${e.message}", e)
            null
        }
    }

    private fun createConnection(urlString: String): HttpURLConnection {
        val url = URI.create(urlString).toURL()
        val connection = url.openConnection() as HttpURLConnection

        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT

        connection.setRequestProperty("User-Agent", USER_AGENT)
        connection.setRequestProperty("Accept", "*/*")
        connection.instanceFollowRedirects = true
        return connection
    }

    private fun getFileNameFromResponse(connection: HttpURLConnection): String? {
        return try {
            val disposition = connection.getHeaderField("Content-Disposition")
            if (disposition.isNullOrBlank()) return null

            val filenamePattern = Regex("filename[\"=]?([^\";]+)")
            val matchResult = filenamePattern.find(disposition)
            matchResult?.groupValues?.get(1)?.trim()?.takeIf { it.contains('.') }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractFileNameFromUrl(urlString: String): String? {
        return try {

            val cleanUrl = urlString.split('?', '#').first()

            val fileName = cleanUrl.substringAfterLast('/')

            if (fileName.contains('.') && fileName.substringAfterLast('.').length >= 2) {
                fileName
            } else {

                "downloaded-file-${System.currentTimeMillis()}.jar"
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun downloadFile(connection: HttpURLConnection, targetFile: File) {
        connection.inputStream.use { inputStream ->
            Files.copy(inputStream, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}

fun Project.downloadFile(
    downloadUrl: String,
    outputDir: File,
    fileName: String? = null
): File? {
    return ExternalModDownloader.download(this, downloadUrl, outputDir, fileName)
}

fun Project.downloadFile(
    downloadUrl: String,
    outputDirPath: String,
    fileName: String? = null
): File? {
    return downloadFile(downloadUrl, file(outputDirPath), fileName)
}
