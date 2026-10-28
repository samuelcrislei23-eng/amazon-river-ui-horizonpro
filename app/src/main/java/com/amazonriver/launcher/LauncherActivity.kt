package com.amazonriver.launcher

import android.content.ComponentName
import android.content.Intent
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.util.Locale

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

data class AppItem(val label: String, val pkg: String, val cls: String)

class LauncherActivity : BaseActivity() {
    private var stamp = -1L
    private var drawerOpen = false
    private var drawer: View? = null
    private var list: ListView? = null
    private var search: EditText? = null
    private var adapter: AppAdapter? = null
    private var allApps: List<AppItem> = emptyList()
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
        col.setPadding(dp(14), dp(6), dp(14), dp(8))
        col.setOnTouchListener { _, e -> detector.onTouchEvent(e); true }

        fun spacer() = View(this).also { col.addView(it, LinearLayout.LayoutParams(MATCH, 0, 1f)) }

        spacer()
        val mark = ImageView(this)
        mark.setImageResource(R.drawable.ar_mark)
        mark.setColorFilter(p.text, PorterDuff.Mode.SRC_IN)
        col.addView(mark, LinearLayout.LayoutParams(dp(60), dp(60)))
        col.addView(arLabel("Amazon River", p, 12f).apply { letterSpacing = 0.22f },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(6) })

        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            textSize = 48f; includeFontPadding = false
            setTextColor(p.text)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        col.addView(clock, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(10) })
        val date = TextClock(this).apply {
            format12Hour = "EEE, d MMM"; format24Hour = "EEE, d MMM"
            textSize = 14f; letterSpacing = 0.1f
            setTextColor(p.muted)
            if (Build.VERSION.SDK_INT >= 28) isAllCaps = true
        }
        col.addView(date, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = dp(2) })
        spacer()

        col.addView(arButton("Todos os apps", p, true) { openDrawer() }.apply { textSize = 14f; minHeight = dp(42) },
            LinearLayout.LayoutParams(MATCH, WRAP))
        val link = arLabel("Configurações", p, 11f, p.muted).apply {
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(8), dp(8), dp(8), dp(2))
            setOnClickListener { openSettings() }
        }
        col.addView(link, LinearLayout.LayoutParams(WRAP, WRAP))
        return col
    }

    private fun buildSearch(p: Palette): View {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = GradientDrawable().apply { setStroke(dp(1), p.text) }
        row.setPadding(dp(10), 0, dp(10), 0)
        val icon = ImageView(this)
        icon.setImageResource(R.drawable.ic_search)
        icon.setColorFilter(p.text, PorterDuff.Mode.SRC_IN)
        row.addView(icon, LinearLayout.LayoutParams(dp(20), dp(20)))
        val et = EditText(this).apply {
            hint = "PESQUISAR"
            setHintTextColor(p.muted)
            setTextColor(p.text)
            textSize = 14f
            letterSpacing = 0.06f
            background = null
            setPadding(dp(8), 0, 0, 0)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { applyFilter(s?.toString() ?: "") }
            })
            setOnEditorActionListener { _, _, _ ->
                adapter?.items?.firstOrNull()?.let { launch(it) }
                true
            }
        }
        search = et
        icon.setOnClickListener {
            et.requestFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(et, 0)
        }
        row.addView(et, LinearLayout.LayoutParams(0, dp(42), 1f))
        return row
    }

    private fun buildDrawer(p: Palette): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setBackgroundColor(p.panel)
        col.setPadding(dp(14), dp(8), dp(14), dp(6))
        col.isClickable = true

        val header = LinearLayout(this)
        header.gravity = Gravity.CENTER_VERTICAL
        header.addView(arLabel("Todos os apps", p, 11f), LinearLayout.LayoutParams(0, WRAP, 1f))
        header.addView(arLabel("Fechar", p, 11f, p.text).apply {
            setPadding(dp(6), dp(8), 0, dp(8))
            setOnClickListener { closeDrawer() }
        })

        val lv = ListView(this)
        lv.divider = ColorDrawable(p.border)
        lv.dividerHeight = 1
        lv.setSelector(ColorDrawable(0x22888888))
        lv.isVerticalScrollBarEnabled = false
        val ad = AppAdapter(emptyList(), p)
        adapter = ad
        lv.adapter = ad
        lv.setOnItemClickListener { _, _, pos, _ -> (ad.getItem(pos) as? AppItem)?.let { launch(it) } }
        list = lv

        val bar = buildSearch(p)
        col.addView(header, LinearLayout.LayoutParams(MATCH, dp(38)))
        if (Prefs.searchTop(this)) {
            col.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = dp(6) })
            col.addView(lv, LinearLayout.LayoutParams(MATCH, 0, 1f))
        } else {
            col.addView(lv, LinearLayout.LayoutParams(MATCH, 0, 1f))
            col.addView(bar, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = dp(6) })
        }
        return col
    }

    private fun applyFilter(q: String) {
        val t = q.trim().lowercase(Locale.getDefault())
        adapter?.items = if (t.isEmpty()) allApps
        else allApps.filter { it.label.lowercase(Locale.getDefault()).contains(t) }
        adapter?.notifyDataSetChanged()
    }

    private fun loadApps(): List<AppItem> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(i, 0)
            .map { AppItem(it.loadLabel(packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }

    private fun openDrawer() {
        val d = drawer ?: return
        if (drawerOpen) return
        allApps = loadApps()
        search?.setText("")
        applyFilter("")
        drawerOpen = true
        d.visibility = View.VISIBLE
        d.translationY = resources.displayMetrics.heightPixels.toFloat()
        d.animate().translationY(0f).setDuration(180).start()
    }

    private fun closeDrawer() {
        val d = drawer ?: return
        drawerOpen = false
        hideKeyboard()
        search?.setText("")
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

    private inner class AppAdapter(var items: List<AppItem>, val p: Palette) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(position: Int): Any = items[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val tv = (convertView as? TextView) ?: TextView(this@LauncherActivity).apply {
                textSize = 14f; letterSpacing = 0.06f; maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                setPadding(dp(2), dp(12), dp(2), dp(12))
            }
            tv.setTextColor(p.text)
            tv.text = items[position].label.uppercase(Locale.getDefault())
            return tv
        }
    }
}
