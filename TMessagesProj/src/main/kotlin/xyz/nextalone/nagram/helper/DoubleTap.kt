package xyz.nextalone.nagram.helper

import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R

object DoubleTap {
    @JvmField
    var doubleTapActionMap: MutableMap<Int, String> =
        HashMap()
    const val DOUBLE_TAP_ACTION_NONE =
        0
    const val DOUBLE_TAP_ACTION_SEND_REACTIONS =
        1
    const val DOUBLE_TAP_ACTION_SHOW_REACTIONS =
        2
    const val DOUBLE_TAP_ACTION_TRANSLATE =
        3
    const val DOUBLE_TAP_ACTION_REPLY =
        4
    const val DOUBLE_TAP_ACTION_SAVE =
        5
    const val DOUBLE_TAP_ACTION_REPEAT =
        6
    const val DOUBLE_TAP_ACTION_REPEAT_AS_COPY =
        7
    const val DOUBLE_TAP_ACTION_EDIT =
        8
    const val DOUBLE_TAP_ACTION_TRANSLATE_LLM =
        9
    const val DOUBLE_TAP_ACTION_DELETE =
        10

    init {
        doubleTapActionMap[DOUBLE_TAP_ACTION_NONE] =
            getString(
                R.string.Disable
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_SEND_REACTIONS] =
            getString(
                R.string.SendReactions
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_SHOW_REACTIONS] =
            getString(
                R.string.ShowReactions
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_TRANSLATE] =
            getString(
                R.string.TranslateMessage
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_REPLY] =
            getString(
                R.string.Reply
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_SAVE] =
            getString(
                R.string.AddToSavedMessages
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_REPEAT] =
            getString(
                R.string.Repeat
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_REPEAT_AS_COPY] =
            getString(
                R.string.RepeatAsCopy
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_EDIT] =
            getString(
                R.string.Edit
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_TRANSLATE_LLM] =
            getString(
                R.string.TranslateMessageLLM
            )
        doubleTapActionMap[DOUBLE_TAP_ACTION_DELETE] =
            getString(
                R.string.Delete
            )
    }
}
