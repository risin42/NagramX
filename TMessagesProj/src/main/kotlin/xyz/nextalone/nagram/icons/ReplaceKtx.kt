package xyz.nextalone.nagram.icons

/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Copyright github.com/arsLan4k1390, 2022-2025.
 */

fun newHashMap(vararg intPairs: Pair<Int, Int>) = HashMap<Int, Int>().apply {
    intPairs.forEach {
        this[it.first] = it.second
    }
}