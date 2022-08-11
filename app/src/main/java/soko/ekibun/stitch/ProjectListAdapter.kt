package soko.ekibun.stitch

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import java.io.File
import java.time.Instant
import java.time.ZoneId

class ProjectListAdapter(private val headerView: View) : BaseAdapter() {

  var projects: Array<File> = emptyArray()

  fun updateProjects() {
    projects = App.getProjects()
    headerView.findViewById<View>(R.id.menu_clear_history).visibility =
      if (projects.isEmpty()) View.INVISIBLE else View.VISIBLE
    notifyDataSetChanged()
  }

  override fun isEnabled(position: Int): Boolean = false

  override fun getCount(): Int = projects.size + 1

  override fun getItem(i: Int): File = projects[i - 1]

  override fun getItemId(i: Int): Long = if (i == 0) 0L else projects[i - 1].hashCode().toLong()

  override fun getView(i: Int, convertView: View?, parent: ViewGroup): View {
    if (i == 0) return headerView
    val view = if (convertView != headerView && convertView != null) convertView else
      LayoutInflater.from(parent.context).inflate(R.layout.list_project, null, false)
    val file = getItem(i)
    val titleView = view.findViewById<TextView>(R.id.text_title)
    titleView.text = file.name.toLongOrNull(16)?.let {
      Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toString().substringBefore('+')
    }
    view.findViewById<View>(R.id.item_view).setOnClickListener {
      EditActivity.startActivity(parent.context, file.name)
    }
    view.findViewById<View>(R.id.menu_delete).setOnClickListener {
      App.deleteProject(file.name)
      updateProjects()
    }
    return view
  }
}
