/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.features.home

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import im.vector.app.R

class FileListAdapter(
        private val onPreviewClicked: (String) -> Unit,
        private val onShareClicked: (String) -> Unit,
        private val onDeleteFile: (String) -> Unit
) : RecyclerView.Adapter<FileListAdapter.FileViewHolder>() {
    private val fileList: MutableList<String> = mutableListOf()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
        return FileViewHolder(view)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        val file = fileList[position]
        holder.bind(file)
    }

    override fun getItemCount(): Int {
        return fileList.size
    }

    fun setData(files: List<String>) {
        fileList.clear()
        fileList.addAll(files)
        notifyDataSetChanged()
    }

    fun removeFile(file: String) {
        fileList.remove(file)
        notifyDataSetChanged()
    }

    inner class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView), View.OnLongClickListener {
        private val fileImageView: ImageView = itemView.findViewById(R.id.fileImageView)
        private val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView)

        init {
            itemView.setOnClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val file = fileList[position]
                    onPreviewClicked(file)
                }
            }
            itemView.setOnLongClickListener(this)
        }

        override fun onLongClick(view: View): Boolean {
            val position = absoluteAdapterPosition
            if (position != RecyclerView.NO_POSITION) {
                val file = fileList[position]
                showMenu(view, file)
                return true
            }
            return false
        }

        private fun showMenu(view: View, file: String) {
            val popupMenu = PopupMenu(view.context, view)
            popupMenu.menuInflater.inflate(R.menu.file_options_menu, popupMenu.menu)
            popupMenu.setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.menu_share -> {
                        onShareClicked(file)
                        true
                    }
                    R.id.menu_delete -> {
                        onDeleteFile(file)
                        true
                    }
                    else -> false
                }
            }
            popupMenu.show()
        }

        fun bind(file: String) {
            fileNameTextView.text = file

            // Create a reference to the file in Firebase Storage
            val userId = SessionManager.session.myUserId
            val storageRef = Firebase.storage.reference
            val fileRef = storageRef.child("files/$userId/saved/$file")

            // Create a temporary file URL to store the downloaded image
            val tempFileUri = Uri.fromFile(itemView.context.cacheDir).buildUpon().appendPath(file).build()
            print("TEST(tempFileUri): $tempFileUri")
            // Download the image from Firebase Storage
            fileRef.getFile(tempFileUri)
                    .addOnSuccessListener {
                        // Image downloaded successfully, load and display it using Glide
                        Glide.with(itemView)
                                .load(tempFileUri)
                                .error(R.drawable.ic_file) // Image to display in case of error
                                .placeholder(R.drawable.ic_file) // Placeholder image while loading
                                .into(fileImageView)
                    }
                    .addOnFailureListener { _ ->
                        // Error occurred while downloading the image, display the error image
                        Glide.with(itemView)
                                .load(R.drawable.ic_file) // Error image
                                .into(fileImageView)
                    }
        }

    }
}
