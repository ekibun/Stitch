package soko.ekibun.stitch

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class EmptyAdapter : RecyclerView.Adapter<EmptyAdapter.EmptyViewHolder>() {
    class EmptyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmptyViewHolder {
        return EmptyViewHolder(parent)
    }

    override fun onBindViewHolder(holder: EmptyViewHolder, position: Int) {
    }

    override fun getItemCount(): Int {
        return 0
    }
}