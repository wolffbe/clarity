package dev.clarity

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * The home screen. Set as the enforced default HOME by PolicyEnforcer, so it is
 * the only launcher the user sees. It shows just the allowed apps and enters
 * lock task (kiosk) mode, which removes the recents/overview surface entirely.
 */
class LauncherActivity : AppCompatActivity() {

    private data class Entry(val label: CharSequence, val icon: Drawable, val launch: Intent)

    private val entries = mutableListOf<Entry>()
    private lateinit var grid: GridView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (12 * d).toInt()
        grid = GridView(this).apply {
            numColumns = 4
            stretchMode = GridView.STRETCH_COLUMN_WIDTH
            verticalSpacing = pad
            setPadding(pad, pad, pad, pad)
            adapter = AppAdapter()
        }
        setContentView(grid)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        (grid.adapter as AppAdapter).notifyDataSetChanged()
        enterKioskIfPermitted()
    }

    /** Enters lock task silently, but only when this app is authorized for it. */
    private fun enterKioskIfPermitted() {
        if (!PolicyEnforcer.isDeviceOwner(this)) return
        if (!PolicyEnforcer.dpm(this).isLockTaskPermitted(packageName)) return
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try { startLockTask() } catch (_: Exception) {}
        }
    }

    private fun loadApps() {
        val pm = packageManager
        entries.clear()
        Whitelist.kioskSet(packageName).forEach { pkg ->
            val launch = pm.getLaunchIntentForPackage(pkg) ?: return@forEach
            val ai = try { pm.getApplicationInfo(pkg, 0) } catch (_: Exception) { return@forEach }
            entries.add(Entry(pm.getApplicationLabel(ai), pm.getApplicationIcon(ai), launch))
        }
        entries.sortBy { it.label.toString().lowercase() }
    }

    private inner class AppAdapter : BaseAdapter() {
        override fun getCount() = entries.size
        override fun getItem(position: Int) = entries[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val d = resources.displayMetrics.density
            val cell = (convertView as? LinearLayout) ?: LinearLayout(this@LauncherActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, (12 * d).toInt(), 0, (12 * d).toInt())
                addView(ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams((56 * d).toInt(), (56 * d).toInt())
                })
                addView(TextView(context).apply {
                    gravity = Gravity.CENTER
                    textSize = 12f
                    maxLines = 1
                    setPadding(0, (6 * d).toInt(), 0, 0)
                })
            }
            val e = entries[position]
            (cell.getChildAt(0) as ImageView).setImageDrawable(e.icon)
            (cell.getChildAt(1) as TextView).text = e.label
            cell.setOnClickListener {
                try { startActivity(e.launch) } catch (_: Exception) {}
            }
            return cell
        }
    }
}
