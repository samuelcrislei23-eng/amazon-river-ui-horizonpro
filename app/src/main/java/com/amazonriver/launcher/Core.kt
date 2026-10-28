package com.amazonriver.launcher

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.TextView
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/** Tokens do Zeta Division: monocromático, plano, cantos retos. */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    val text = if (dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
    val muted = if (dark) 0xFF8A8A8A.toInt() else 0xFF777777.toInt()
    val border = if (dark) 0xFF2A2A2A.toInt() else 0xFFE5E5E5.toInt()
    val solid = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val onSolid = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    val scrim = if (dark) 0xB3000000.toInt() else 0xB3FFFFFF.toInt()
    val panel = if (dark) 0xF2000000.toInt() else 0xF2FFFFFF.toInt()
}

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("ar_launcher", Context.MODE_PRIVATE)
    fun isDark(c: Context) = sp(c).getBoolean("dark", false)
    fun setDark(c: Context, v: Boolean) = sp(c).edit().putBoolean("dark", v).putLong("stamp", System.nanoTime()).apply()
    fun touch(c: Context) = sp(c).edit().putLong("stamp", System.nanoTime()).apply()
    fun stamp(c: Context) = sp(c).getLong("stamp", 0L)
    fun searchTop(c: Context) = sp(c).getBoolean("search_top", true)
    fun setSearchTop(c: Context, v: Boolean) = sp(c).edit().putBoolean("search_top", v).putLong("stamp", System.nanoTime()).apply()
}

object Wallpaper {
    private fun file(c: Context) = File(c.filesDir, "wallpaper.jpg")
    fun exists(c: Context) = file(c).exists()
    fun clear(c: Context) { file(c).delete(); Prefs.touch(c) }
    fun load(c: Context): Bitmap? = if (exists(c)) BitmapFactory.decodeFile(file(c).path) else null

    fun save(c: Context, uri: Uri): Boolean = try {
        val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
            android.graphics.ImageDecoder.decodeBitmap(
                android.graphics.ImageDecoder.createSource(c.contentResolver, uri)
            ) { decoder, info, _ ->
                val big = maxOf(info.size.width, info.size.height)
                if (big > 1600) {
                    val s = 1600f / big
                    decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
                }
                decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
            var n = 1
            while (b.outWidth / n > 1600 || b.outHeight / n > 1600) n *= 2
            val o = BitmapFactory.Options().apply { inSampleSize = n }
            c.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        }
        if (bmp == null) false else {
            FileOutputStream(file(c)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            true
        }
    } catch (e: Exception) { false }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

fun ripple(color: Int, content: Drawable?): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(color), content, ColorDrawable(Color.WHITE))

@Suppress("DEPRECATION")
fun Activity.applyBars(p: Palette) {
    window.statusBarColor = p.bg
    window.navigationBarColor = p.bg
    var f = 0
    if (!p.dark) {
        f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }
    window.decorView.systemUiVisibility = f
}

fun Context.arLabel(text: String, p: Palette, size: Float = 10f, color: Int = p.muted): TextView =
    TextView(this).apply {
        this.text = text.uppercase(Locale.getDefault())
        setTextColor(color)
        textSize = size
        letterSpacing = 0.12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

fun Context.arButton(text: String, p: Palette, filled: Boolean, onClick: () -> Unit): TextView =
    TextView(this).apply {
        this.text = text.uppercase(Locale.getDefault())
        textSize = 11f
        letterSpacing = 0.1f
        gravity = Gravity.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (filled) p.onSolid else p.text)
        val shape = GradientDrawable().apply {
            setColor(if (filled) p.solid else Color.TRANSPARENT)
            setStroke(dp(1), if (filled) p.solid else p.text)
        }
        background = ripple(0x33888888, shape)
        minHeight = dp(34)
        setPadding(dp(12), 0, dp(12), 0)
        isClickable = true
        setOnClickListener { onClick() }
    }

/** Normaliza a densidade: a tela vira ~224dp de largura, então dp/sp ficam proporcionais a qualquer aparelho. */
open class BaseActivity : Activity() {
    override fun attachBaseContext(base: Context) {
        val cfg = Configuration(base.resources.configuration)
        val wpx = base.resources.displayMetrics.widthPixels
        cfg.densityDpi = (wpx / 224f * 160f).toInt()
        super.attachBaseContext(base.createConfigurationContext(cfg))
    }
}
