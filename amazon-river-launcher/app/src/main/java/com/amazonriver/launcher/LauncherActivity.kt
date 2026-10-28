package com.amazonriver.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

data class AppItem(val label: String, val pkg: String, val cls: String)

class LauncherActivity : Activity() {
    private var stamp = -1L
    private var drawerOpen = false
    private var drawer: View? = null
    private var list: ListView? = null
    private lateinit var detector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (vy < -900f && Math.abs(vy) > Math.abs(vx)) { openDrawer(); return true }
                return false
            }
            override fun onLongPress(e: MotionEvent) { openSettings() }
        })
        build()
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.stamp(this) != stamp) build()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (drawerOpen) closeDrawer()
    }

    override fun onBackPressed() { if (drawerOpen) closeDrawer() }

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun build() {
        stamp = Prefs.stamp(this)
        val p = Palette(Prefs.isDark(this))
        applyBars(p)
        val root = FrameLayout(this)
        root.setBackgroundColor(p.bg)
        Wallpaper.load(this)?.let { bmp ->
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(bmp)
            root.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
            val scrim = View(this)
            scrim.setBackgroundColor(p.scrim)
            root.addView(scrim, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        root.addView(buildHome(p), FrameLayout.LayoutParams(MATCH, MATCH))
        val d = buildDrawer(p)
        d.visibility = View.GONE
        drawer = d
        drawerOpen = false
        root.addView(d, FrameLayout.LayoutParams(MATCH, MATCH))
        setContentView(root)
    }

    private fun buildHome(p: Palette): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL
        col.setPadding(dp(16), dp(10), dp(16), dp(12))
        col.setOnTouchListener { _, e -> detector.onTouchEvent(e); true }

        fun spacer() = View(this).also { col.addView(it, LinearLayout.LayoutParams(MATCH, 0, 1f)) }

        spacer()
        val mark = ImageView(this)
        mark.setImageResource(R.drawable.ar_mark)
        mark.setColorFilter(p.text, PorterDuff.Mode.SRC_IN)
        col.addView(mark, LinearLayout.LayoutParams(dp(40), dp(40)))
        col.addView(arLabel("Amazon River", p, 10f).apply { letterSpacing = 0.24f },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6) })

        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            textSize = 38f; includeFontPadding = false
            setTextColor(p.text)
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        }
        col.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(14) })
        val date = TextClock(this).apply {
            format12Hour = "EEE, d MMM"; format24Hour = "EEE, d MMM"
            textSize = 11f; letterSpacing = 0.1f
            setTextColor(p.muted)
            if (Build.VERSION.SDK_INT >= 28) isAllCaps = true
        }
        col.addView(date, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })
        spacer()

        col.addView(arButton("Todos os apps", p, true) { openDrawer() }, LinearLayout.LayoutParams(MATCH, WRAP))
        val link = arLabel("Configurações", p, 10f, p.muted).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(8), dp(8), dp(8), dp(2))
            setOnClickListener { openSettings() }
        }
        col.addView(link, LinearLayout.LayoutParams(WRAP, WRAP))
        return col
    }

    private fun buildDrawer(p: Palette): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(p.panel)
        col.setPadding(dp(14), dp(8), dp(14), dp(4))
        col.isClickable = true

        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(arLabel("Todos os apps", p), LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(arLabel("Fechar", p, 10f, p.text).apply {
            setPadding(dp(6), dp(8), 0, dp(8))
            setOnClickListener { closeDrawer() }
        })
        col.addView(header, LinearLayout.LayoutParams(MATCH, dp(38)))

        val lv = ListView(this)
        lv.divider = ColorDrawable(p.border)
        lv.dividerHeight = 1
        lv.setSelector(ColorDrawable(0x22888888))
        lv.isVerticalScrollBarEnabled = false
        lv.setOnItemClickListener { _, _, pos, _ -> (lv.adapter.getItem(pos) as? AppItem)?.let { launch(it) } }
        lv.adapter = AppAdapter(emptyList(), p)
        list = lv
        col.addView(lv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        return col
    }

    private fun loadApps(): List<AppItem> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, 0)
            .map { AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun openDrawer() {
        val d = drawer ?: return
        if (drawerOpen) return
        list?.adapter = AppAdapter(loadApps(), Palette(Prefs.isDark(this)))
        drawerOpen = true
        d.visibility = View.VISIBLE
        d.translationY = resources.displayMetrics.heightPixels.toFloat()
        d.animate().translationY(0f).setDuration(180).start()
    }

    private fun closeDrawer() {
        val d = drawer ?: return
        drawerOpen = false
        d.animate().translationY(resources.displayMetrics.heightPixels.toFloat()).setDuration(160)
            .withEndAction { if (!drawerOpen) d.visibility = View.GONE }.start()
    }

    private fun launch(a: AppItem) {
        try {
            startActivity(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    .setComponent(ComponentName(a.pkg, a.cls))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Não foi possível abrir", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class AppAdapter(val items: List<AppItem>, val p: Palette) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(this@LauncherActivity).apply {
                textSize = 13f; letterSpacing = 0.06f; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding(dp(2), dp(11), dp(2), dp(11))
            }
            tv.setTextColor(p.text)
            tv.text = items[position].label.uppercase(Locale.getDefault())
            return tv
        }
    }
}
