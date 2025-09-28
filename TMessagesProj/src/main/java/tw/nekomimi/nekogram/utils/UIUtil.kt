package tw.nekomimi.nekogram.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

object UIUtil {

    fun runOnIoDispatcher(runnable: suspend () -> Unit) {
        GlobalScope.launch(Dispatchers.IO) {
            runnable()
        }
    }

}