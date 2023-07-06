package im.vector.app.features.home

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import im.vector.app.R
import org.matrix.android.sdk.api.session.Session
import java.io.File

class Storage(session: Session) {
    private val userId = session.myUserId

    companion object {
        private const val TAG = "Storage"
        const val storageUrl = "gs://bigstarconnect.appspot.com"
    }

    fun fetchFileList(onFileListFetched: (List<String>) -> Unit) {
        val storage = Firebase.storage(storageUrl)
        val storageRef = storage.reference

        val filesRef = storageRef.child("files/$userId/saved")

        filesRef.listAll()
                .addOnSuccessListener { listResult ->
                    val fileList = listResult.items.map { it.name }
                    onFileListFetched(fileList)
                }
                .addOnFailureListener { _ ->
                    Log.e(TAG, "Error fetching file list")
                }
    }

    fun deleteFile(file: String) {
        val storage = Firebase.storage(storageUrl)
        val storageRef = storage.reference

        // Get a reference to the file in Firebase Cloud Storage
        val fileRef = storageRef.child("files/$userId/saved/$file")

        // Delete the file
        fileRef.delete()
                .addOnSuccessListener {
                    Log.d(TAG, "File deleted: $file")
                }
                .addOnFailureListener { _ ->
                    Log.e(TAG, "Error deleting file")
                }
    }

    fun previewFile(activity: Activity, file: String) {
        val userId = SessionManager.session.myUserId
        val storageRef = Firebase.storage(storageUrl).reference
        val fileRef = storageRef.child("files/$userId/saved/$file")

        val tempFile = File(activity.cacheDir, file)

        fileRef.getFile(tempFile)
                .addOnSuccessListener {
                    // Create a content URI using FileProvider
                    val fileUri = FileProvider.getUriForFile(
                            activity,
                            "${activity.packageName}.fileProvider",
                            tempFile
                    )

                    // Determine the MIME type of the file
                    val mimeType = activity.contentResolver.getType(fileUri)

                    // Create an intent to open the file with an appropriate viewer based on the MIME type
                    val openIntent = Intent(Intent.ACTION_VIEW)
                    openIntent.setDataAndType(fileUri, mimeType)
                    openIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    // Verify that there is an app available to handle the intent
                    if (openIntent.resolveActivity(activity.packageManager) != null) {
                        activity.startActivity(openIntent)
                    } else {
                        Log.e(TAG, "No app available to open file")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error downloading file", exception)
                }
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    Log.d(TAG, "Download progress: $progress%")
                }
    }


    fun shareFile(activity: Activity, file: String) {
        val storage = Firebase.storage(storageUrl)
        val storageRef = storage.reference
        val fileRef = storageRef.child("files/$userId/saved/$file")

        val tempFile = File(activity.cacheDir, file)

        fileRef.getFile(tempFile)
                .addOnSuccessListener {
                    val authority = "${activity.packageName}.fileProvider"
                    val tempFileUri = FileProvider.getUriForFile(activity, authority, tempFile)

                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "*/*"
                    shareIntent.putExtra(Intent.EXTRA_STREAM, tempFileUri)
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    activity.startActivity(Intent.createChooser(shareIntent, "Share File"))
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error downloading file", exception)
                }
                .addOnProgressListener { taskSnapshot ->
                    val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount).toInt()
                    Log.d(TAG, "Download progress: $progress%")
                }
    }

//    class FileAdapter(
//            private val fileList: List<String>,
//            private val onDelete: (String) -> Unit,
//            private val userId: String // Add userId parameter
//    ) : RecyclerView.Adapter<FileAdapter.FileViewHolder>() {
//
//        class FileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
//            val thumbnailImageView: ImageView = itemView.findViewById(R.id.fileImageView)
//            val fileNameTextView: TextView = itemView.findViewById(R.id.fileNameTextView)
//            val deleteButton: ImageView = itemView.findViewById(R.id.deleteButton)
//        }
//
//        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
//            val itemView = LayoutInflater.from(parent.context)
//                    .inflate(R.layout.item_file, parent, false)
//            return FileViewHolder(itemView)
//        }
//
//        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
//            val file = fileList[position]
//
//            holder.fileNameTextView.text = file
//
//            val storage = Firebase.storage(storageUrl)
//            val storageRef = storage.reference
//
//            val fileRef = storageRef.child("files/$userId/saved/$file")
//
//            fileRef.downloadUrl.addOnSuccessListener { uri ->
//                Glide.with(holder.itemView)
//                        .load(uri)
//                        .placeholder(R.drawable.ic_file)
//                        .error(R.drawable.ic_file)
//                        .into(holder.thumbnailImageView)
//            }.addOnFailureListener { _ ->
//                Log.e(TAG, "Error loading file thumbnail")
//            }
//
//            holder.deleteButton.setOnClickListener {
//                onDelete(file)
//            }
//        }
//
//        override fun getItemCount(): Int {
//            return fileList.size
//        }
//    }
}
