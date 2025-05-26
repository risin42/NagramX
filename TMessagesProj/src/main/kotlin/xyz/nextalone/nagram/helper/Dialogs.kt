package xyz.nextalone.nagram.helper

import android.content.Context
import android.content.DialogInterface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.MarginLayoutParams
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper

object Dialogs {

    @JvmStatic
    fun createNeedChangeNekoSettingsAlert(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle(getString(R.string.AppName))
        builder.setMessage(getString(R.string.NeedChangeNekoSettings))
        builder.setPositiveButton(getString(R.string.OK), null)
        builder.show()
    }
}
