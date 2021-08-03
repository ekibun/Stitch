package soko.ekibun.stitch

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class EditAdapter : RecyclerView.Adapter<EditAdapter.EditViewHolder>() {
    class EditViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EditViewHolder {
        return EditViewHolder(parent)
    }

    override fun onBindViewHolder(holder: EditViewHolder, position: Int) {
    }

    override fun getItemCount(): Int {
        return 0
    }
}