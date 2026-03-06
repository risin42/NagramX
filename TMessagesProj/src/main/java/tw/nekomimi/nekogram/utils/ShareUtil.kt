package tw.nekomimi.nekogram.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.telegram.messenger.BuildConfig
import org.telegram.ui.LaunchActivity
import java.io.File

object ShareUtil {

    @JvmOverloads
    @JvmStatic
    fun shareFile(ctx: Context, fileToShare: File, caption: String = "") {

        val uri =
            FileProvider.getUriForFile(ctx, BuildConfig.APPLICATION_ID + ".provider", fileToShare)

        val i = Intent(Intent.ACTION_SEND)

        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        i.type = "message/rfc822"
        i.putExtra(Intent.EXTRA_EMAIL, "")

        if (caption.isNotBlank()) i.putExtra(Intent.EXTRA_SUBJECT, caption)

        i.putExtra(Intent.EXTRA_STREAM, uri)
        i.setClass(ctx, LaunchActivity::class.java)

        ctx.startActivity(i)

    }

}
