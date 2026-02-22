package tw.nekomimi.nekogram.utils

import android.os.Build
import okhttp3.internal.closeQuietly
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.FileLog
import java.io.File
import java.util.zip.ZipFile

object FileUtil {

    @JvmStatic
    fun deleteDirectory(directoryToBeDeleted: File): Boolean {
        val allContents = directoryToBeDeleted.listFiles()
        if (allContents != null) {
            for (file in allContents) {
                deleteDirectory(file)
            }
        }
        return directoryToBeDeleted.delete()
    }

    @JvmStatic
    fun initDir(dir: File) {

        var parentDir: File? = dir

        while (parentDir != null) {

            if (parentDir.isDirectory) break

            if (parentDir.isFile) parentDir.deleteRecursively()

            parentDir = parentDir.parentFile

        }

        dir.mkdirs()

        // ignored

    }

    @JvmStatic
    @JvmOverloads
    fun delete(file: File?, filter: (File) -> Boolean = { true }) {

        runCatching {

            file?.takeIf { filter(it) }?.deleteRecursively()

        }

    }

    @JvmStatic
    fun initFile(file: File) {

        file.parentFile?.also { initDir(it) }

        if (!file.isFile) {

            if (file.isDirectory) file.deleteRecursively()

            if (!file.isFile) {

                if (!file.createNewFile() && !file.isFile) {

                    error("unable to create file ${file.path}")

                }

            }

        }

    }

    @JvmStatic
    fun readUtf8String(file: File) = file.readText()

    @JvmStatic
    fun writeUtf8String(text: String, save: File) {

        initFile(save)

        save.writeText(text)

    }

}
