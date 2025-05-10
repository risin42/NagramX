/**
 * This is the source code of Cherrygram for Android.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 * Please, be respectful and credit the original author if you use this code.
 *
 * Copyright github.com/arsLan4k1390, 2022-2025.
 */

package xyz.nextalone.nagram.icons

import android.annotation.SuppressLint
import android.content.res.*
import android.graphics.drawable.Drawable
import xyz.nextalone.nagram.NaConfig

@Suppress("DEPRECATION")
class IconsResources(private val wrapped: Resources) :
    Resources(wrapped.assets, wrapped.displayMetrics, wrapped.configuration) {

    private var activeReplacement: BaseIconReplace = getCurrentIconPack()

    fun reloadReplacements() {
        activeReplacement = getCurrentIconPack()
        clearCache()
    }

    private val drawableCache =
        object : LinkedHashMap<Triple<Int, Int?, Int?>, Drawable?>(300, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Triple<Int, Int?, Int?>, Drawable?>?): Boolean {
                if (size > 300) {
                    clearCache()
                    return true
                }
                return false
            }
        }

    private var cacheHits = 0
    private var cacheMisses = 0

    private fun clearCache() {
        drawableCache.clear()
        cacheHits = 0
        cacheMisses = 0
    }

    private fun getCurrentIconPack(): BaseIconReplace {
        return when (NaConfig.iconReplacements.Int()) {
            ICON_REPLACE_SOLAR -> SolarIconReplace()
            else -> NoIconReplace()
        }
    }

    private fun getCachedDrawable(
        cacheKey: Triple<Int, Int?, Int?>, loader: () -> Drawable?
    ): Drawable? {
        return drawableCache.getOrPut(cacheKey) {
            cacheMisses++
            loader()
        }?.let { drawable ->
            drawable.constantState?.newDrawable()?.mutate() ?: drawable
        }?.also {
            cacheHits++
        }
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("UseCompatLoadingForDrawables")
    @Throws(NotFoundException::class)
    override fun getDrawable(id: Int): Drawable? {
        val wrappedId = activeReplacement.wrap(id)
        val cacheKey = Triple(wrappedId, null, null)

        return getCachedDrawable(cacheKey) { wrapped.getDrawable(wrappedId) }
    }

    @SuppressLint("UseCompatLoadingForDrawables")
    @Throws(NotFoundException::class)
    override fun getDrawable(id: Int, theme: Theme?): Drawable? {
        val wrappedId = activeReplacement.wrap(id)
        val cacheKey = Triple(wrappedId, null, theme?.hashCode())

        return getCachedDrawable(cacheKey) { wrapped.getDrawable(wrappedId, theme) }
    }

    @Deprecated("Deprecated in Java")
    @Throws(NotFoundException::class)
    override fun getDrawableForDensity(id: Int, density: Int): Drawable? {
        val wrappedId = activeReplacement.wrap(id)
        val cacheKey = Triple(wrappedId, density, null)

        return getCachedDrawable(cacheKey) {
            wrapped.getDrawableForDensity(
                wrappedId, density
            )
        }
    }

    override fun getDrawableForDensity(id: Int, density: Int, theme: Theme?): Drawable? {
        val wrappedId = activeReplacement.wrap(id)
        val cacheKey = Triple(wrappedId, density, theme?.hashCode())

        return getCachedDrawable(cacheKey) {
            wrapped.getDrawableForDensity(
                wrappedId, density, theme
            )
        }
    }

    companion object {
        const val ICON_REPLACE_SOLAR = 1
    }
}