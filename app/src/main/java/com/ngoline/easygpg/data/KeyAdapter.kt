package com.ngoline.easygpg.data

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import com.ngoline.easygpg.R

class KeyAdapter(private val keys: MutableList<KeyItem>, private val context: Context) : BaseAdapter() {
    override fun getCount(): Int = keys.size

    override fun getItem(position: Int): Any = keys[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(parent?.context).inflate(R.layout.key_item, parent, false)
        val keyItem = getItem(position) as KeyItem
        view.findViewById<TextView>(R.id.textAlias).text = keyItem.alias
        view.findViewById<TextView>(R.id.textFingerprint).text =
            context.getString(R.string.key_fingerprint, keyItem.fingerprint.takeLast(8))

        val deleteButton = view.findViewById<Button>(R.id.buttonDelete)
        deleteButton.setOnClickListener {
            removeKey(position)
        }

        return view
    }

    fun addKey(keyItem: KeyItem) {
        keys.add(keyItem)
        notifyDataSetChanged()
    }

    private fun removeKey(position: Int) {
        val key = keys[position]
        // Delete the key file
        context.deleteFile("${key.alias}.imported.pgp")
        // Remove the key from the list and refresh the ListView
        keys.removeAt(position)
        notifyDataSetChanged()
    }
}