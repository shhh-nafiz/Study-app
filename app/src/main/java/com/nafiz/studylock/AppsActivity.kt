package com.nafiz.studylock

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

class AppsActivity : Activity() {
    private val selected = HashSet<String>()
    private lateinit var list: LinearLayout
    private var themeVer = C.version

    override fun onResume() {
        super.onResume()
        if (themeVer != C.version) recreate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()
        selected.addAll(Store.blocked(this))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(8))
        }
        root.addView(header("Blocked apps"), lp())
        root.addView(tv("Tick the apps to lock during study sessions.", 14f, C.MUTED), lp(8))

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(list) }
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
        setContentView(root)

        Thread {
            val pm = packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val items = pm.queryIntentActivities(intent, 0)
                .filter { it.activityInfo.packageName != packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { Triple(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
                .sortedBy { it.second.lowercase() }
            runOnUiThread { items.forEach { list.addView(appRow(it.first, it.second, it.third)) } }
        }.start()
    }

    private fun appRow(pkg: String, name: String, icon: Drawable): LinearLayout {
        val cb = CheckBox(this).apply {
            isChecked = pkg in selected
            buttonTintList = ColorStateList.valueOf(C.ACCENT)
            isClickable = false
        }
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }
        r.addView(ImageView(this).apply { setImageDrawable(icon) },
            LinearLayout.LayoutParams(dp(40), dp(40)))
        r.addView(tv(name, 16f, C.TEXT).apply { setPadding(dp(16), 0, 0, 0) },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(cb)
        r.setOnClickListener {
            if (cb.isChecked) {
                if (Store.isRunning(this)) {
                    Toast.makeText(this, "Can't unblock apps during a session", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                cb.isChecked = false
                selected.remove(pkg)
            } else {
                cb.isChecked = true
                selected.add(pkg)
            }
            Store.setBlocked(this, selected)
        }
        return r
    }
}
