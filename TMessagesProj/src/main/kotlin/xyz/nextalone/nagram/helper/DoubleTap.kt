package xyz.nextalone.nagram.helper

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R

object DoubleTap {
    const val DOUBLE_TAP_ACTION_NONE = 0
    const val DOUBLE_TAP_ACTION_SEND_REACTIONS = 1
    const val DOUBLE_TAP_ACTION_SHOW_REACTIONS = 2
    const val DOUBLE_TAP_ACTION_TRANSLATE = 3
    const val DOUBLE_TAP_ACTION_REPLY = 4
    const val DOUBLE_TAP_ACTION_SAVE = 5
    const val DOUBLE_TAP_ACTION_REPEAT = 6
    const val DOUBLE_TAP_ACTION_REPEAT_AS_COPY = 7
    const val DOUBLE_TAP_ACTION_EDIT = 8
    const val DOUBLE_TAP_ACTION_TRANSLATE_LLM = 9
    const val DOUBLE_TAP_ACTION_DELETE = 10

    // Use lazy initialization to defer getString calls until first access
    // This improves startup performance and allows language changes to take effect
    @JvmStatic
    val doubleTapActionMap: Map<Int, String> by lazy {
        mapOf(
            DOUBLE_TAP_ACTION_NONE to getString(R.string.Disable),
            DOUBLE_TAP_ACTION_SEND_REACTIONS to getString(R.string.SendReactions),
            DOUBLE_TAP_ACTION_SHOW_REACTIONS to getString(R.string.ShowReactions),
            DOUBLE_TAP_ACTION_TRANSLATE to getString(R.string.TranslateMessage),
            DOUBLE_TAP_ACTION_REPLY to getString(R.string.Reply),
            DOUBLE_TAP_ACTION_SAVE to getString(R.string.AddToSavedMessages),
            DOUBLE_TAP_ACTION_REPEAT to getString(R.string.Repeat),
            DOUBLE_TAP_ACTION_REPEAT_AS_COPY to getString(R.string.RepeatAsCopy),
            DOUBLE_TAP_ACTION_EDIT to getString(R.string.Edit),
            DOUBLE_TAP_ACTION_TRANSLATE_LLM to getString(R.string.TranslateMessageLLM),
            DOUBLE_TAP_ACTION_DELETE to getString(R.string.Delete)
        )
    }
}
