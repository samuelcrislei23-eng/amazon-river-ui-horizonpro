package com.amazonriver.launcher

import android.app.Activity
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class SettingsActivity : BaseActivity() {
    private val reqPick = 41

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        render()
    }

    private fun render() {
        val p = Palette(Prefs.isDark(this))
        applyBars(p)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(p.bg)
        scroll.isVerticalScrollBarEnabled = false
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(14), dp(16), dp(24))
        scroll.addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        fun add(v: View, top: Int = 0, w: Int = ViewGroup.LayoutParams.MATCH_PARENT) =
            col.addView(v, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(top) })

        fun section(title: String) {
            add(View(this).apply { setBackgroundColor(p.border) }.also {
                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { topMargin = dp(22) }
            })
            add(arLabel(title, p), 12)
        }

        val mark = ImageView(this)
        mark.setImageResource(R.drawable.ar_mark)
        mark.setColorFilter(p.text, PorterDuff.Mode.SRC_IN)
        col.addView(mark, LinearLayout.LayoutParams(dp(30), dp(30)))
        add(TextView(this).apply {
            text = "CONFIGURAÇÕES"; textSize = 20f; letterSpacing = 0.04f
            setTextColor(p.text); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }, 10)
        add(arLabel("Amazon River Launcher", p), 2)

        // Tema
        section("Tema")
        val row = LinearLayout(this)
        row.addView(arButton("Claro", p, !p.dark) { Prefs.setDark(this, false); render() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        row.addView(arButton("Escuro", p, p.dark) { Prefs.setDark(this, true); render() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
        add(row, 10)

        // Pesquisa
        section("Barra de pesquisa")
        val srow = LinearLayout(this)
        srow.addView(arButton("Em cima", p, Prefs.searchTop(this)) { Prefs.setSearchTop(this, true); render() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        srow.addView(arButton("Embaixo", p, !Prefs.searchTop(this)) { Prefs.setSearchTop(this, false); render() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(4) })
        add(srow, 10)

        // Papel de parede
        section("Papel de parede")
        val box = FrameLayout(this)
        box.background = GradientDrawable().apply { setStroke(dp(1), p.border) }
        val bmp = Wallpaper.load(this)
        if (bmp != null) {
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.setImageBitmap(bmp)
            box.addView(iv, FrameLayout.LayoutParams(-1, -1))
        } else {
            box.addView(arLabel("Sem imagem", p), FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        col.addView(box, LinearLayout.LayoutParams(-1, dp(90)).apply { topMargin = dp(10) })
        add(arButton("Escolher imagem", p, true) { pickImage() }, 8)
        if (bmp != null) add(arButton("Remover", p, false) { Wallpaper.clear(this); render() }, 8)

        // Launcher padrão
        section("Launcher padrão")
        add(TextView(this).apply {
            text = "Defina o Amazon River como tela inicial do aparelho."
            textSize = 11f; setTextColor(p.muted)
        }, 8)
        add(arButton("Definir como padrão", p, false) {
            try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }, 10)

        add(arLabel("v1.1.0 · AMOLED 448 × 558", p, 9f), 24)
        setContentView(scroll)
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*")
        try { startActivityForResult(i, reqPick) }
        catch (e: Exception) {
            try { startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), reqPick) }
            catch (e2: Exception) { Toast.makeText(this, "Nenhum seletor de imagens", Toast.LENGTH_SHORT).show() }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != reqPick || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread {
            val ok = Wallpaper.save(this, uri)
            runOnUiThread {
                if (ok) { Prefs.touch(this); render() }
                else Toast.makeText(this, "Falha ao carregar a imagem", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }
}
