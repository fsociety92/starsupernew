package im.vector.app.features.home

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.ToggleButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amulyakhare.textdrawable.TextDrawable
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import im.vector.app.R
import im.vector.app.core.contacts.ContactsDataSource
import im.vector.app.core.contacts.MappedContact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.matrix.android.sdk.api.session.getRoom
import org.matrix.android.sdk.api.session.identity.ThreePid
import org.matrix.android.sdk.api.session.room.members.roomMemberQueryParams
import org.matrix.android.sdk.api.session.room.model.Membership
import org.matrix.android.sdk.api.session.room.model.RoomSummary
import org.matrix.android.sdk.api.session.room.model.RoomType
import org.matrix.android.sdk.api.session.room.roomSummaryQueryParams
import org.matrix.android.sdk.api.util.MatrixItem
import timber.log.Timber
import java.util.UUID

class CloudShareActivity : AppCompatActivity() {
    private lateinit var progressBar: ProgressBar
    private lateinit var roomList: RecyclerView
    private lateinit var contactList: RecyclerView
    private lateinit var adapter: RoomAdapter
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var selectFileButton: View
    private lateinit var selectFromGalleryButton: View
    private lateinit var showContactsSwitch: ToggleButton
    private var selectedFileUri: Uri? = null
    private var mappedContacts: List<MappedContact> = emptyList()
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.Main)
    private val CONTACTS_PERMISSION_REQUEST_CODE = 1

    companion object {
        private const val TAG = "CloudShare"
    }

    private val filePickerLauncher =
            registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
                handleSelectedFile(uri)
            }

    private val galleryPickerLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    val uri: Uri? = data?.data
                    handleSelectedFile(uri)
                }
            }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cloud_share)
        checkContactsPermission()

        progressBar = findViewById(R.id.progress_bar)

        roomList = findViewById(R.id.room_list)
        roomList.layoutManager = LinearLayoutManager(this)
        roomList.visibility = View.GONE

        contactList = findViewById(R.id.contact_list)
        contactList.layoutManager = LinearLayoutManager(this)
        contactList.visibility = View.GONE

        selectFileButton = findViewById(R.id.select_file_button)
        selectFileButton.setOnClickListener {
            openFileSelection()
        }

        selectFromGalleryButton = findViewById(R.id.select_from_gallery_button)
        selectFromGalleryButton.setOnClickListener {
            openGallerySelection()
        }

        showContactsSwitch = findViewById(R.id.show_contacts_switch)
        showContactsSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                contactList.visibility = View.VISIBLE
                roomList.visibility = View.GONE
            } else {
                contactList.visibility = View.GONE
                roomList.visibility = View.VISIBLE
            }
        }
        showContactsSwitch.visibility = View.GONE



        // Fetch the room summaries
        val session = SessionManager.session
        val roomSummaryList = session.roomService().getRoomSummaries(roomSummaryQueryParams {
            excludeType = listOf(RoomType.SPACE)
        })


        adapter = RoomAdapter(roomSummaryList)
        roomList.adapter = adapter
    }

    private inner class ContactAdapter(private val contacts: List<MappedContact>) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {
        private var selectedPosition: Int = RecyclerView.NO_POSITION

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_contact_simple, parent, false)
            return ContactViewHolder(view)
        }

        override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
            val contact = contacts[position]
            holder.bind(contact)
        }

        override fun getItemCount(): Int {
            return contacts.size
        }

        inner class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val avatarImageView: ImageView = itemView.findViewById(R.id.avatar_image)
            private val displayNameTextView: TextView = itemView.findViewById(R.id.display_name_text)
            private val msisdnTextView: TextView = itemView.findViewById(R.id.msisdn_text)

            init {
                itemView.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val contact = contacts[position]
                        onContactClicked(contact)
                    }

                    val isSelected = position == selectedPosition
                    itemView.isActivated = !isSelected
                    selectedPosition = position

                    notifyDataSetChanged()
                }
            }

            fun bind(contact: MappedContact) {
                var matrixItemId = UUID.randomUUID().toString()
                displayNameTextView.text = contact.displayName

                // Check if the contact has at least one msisdn with matrixId
                val msisdnsWithMatrixId = contact.msisdns.filter { it.matrixId != null }
                if (msisdnsWithMatrixId.isNotEmpty()) {
                    val msisdn = msisdnsWithMatrixId.first()
                    msisdnTextView.text = msisdn.matrixId
                    matrixItemId = msisdn.matrixId!!
                } else {
                    msisdnTextView.text = ""
                }

                val matrixItem = MatrixItem.UserItem(matrixItemId, contact.displayName)
                val placeholderDrawable = getPlaceholderDrawable(matrixItem)
                Glide.with(itemView)
                        .load(contact.photoURI)
                        .placeholder(placeholderDrawable)
                        .circleCrop()
                        .into(avatarImageView)

                itemView.isActivated = bindingAdapterPosition == selectedPosition
            }
        }
    }

    private inner class RoomAdapter(private val roomSummaries: List<RoomSummary>) :
            RecyclerView.Adapter<RoomAdapter.RoomViewHolder>() {
        private var selectedPosition: Int = RecyclerView.NO_POSITION

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RoomViewHolder {
            val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_room_simple, parent, false)
            return RoomViewHolder(view)
        }

        override fun onBindViewHolder(holder: RoomViewHolder, position: Int) {
            val roomSummary = roomSummaries[position]
            holder.bind(roomSummary)
        }

        override fun getItemCount(): Int {
            return roomSummaries.size
        }

        private inner class RoomViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val avatarImageView: ImageView = view.findViewById(R.id.avatar_image)
            private val displayNameTextView: TextView = view.findViewById(R.id.display_name_text)

            init {
                itemView.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) {
                        val roomSummary = roomSummaries[position]
                        onRoomClicked(roomSummary)
                    }

                    val isSelected = position == selectedPosition
                    itemView.isActivated = !isSelected
                    selectedPosition = position

                    notifyDataSetChanged()
                }
            }

            fun bind(roomSummary: RoomSummary) {
                displayNameTextView.text = roomSummary.displayName

                // Load the avatar image
                val matrixItem = MatrixItem.RoomItem(roomSummary.roomId, roomSummary.displayName)
                val avatarUrl = roomSummary.avatarUrl
                val placeholderDrawable = getPlaceholderDrawable(matrixItem)
                Glide.with(itemView)
                        .load(avatarUrl)
                        .placeholder(placeholderDrawable)
                        .circleCrop()
                        .into(avatarImageView)

                itemView.isActivated = bindingAdapterPosition == selectedPosition
            }
        }
    }

    private fun checkContactsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    CONTACTS_PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission is already granted, perform the contact-related operations
            setContacts()
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTACTS_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission is granted, perform the contact-related operations
                setContacts()
            } else {
                // Permission is denied, handle the scenario accordingly (e.g., show an error message)
                finish()
            }
        }
    }

    private fun getPlaceholderDrawable(matrixItem: MatrixItem): Drawable {
        val avatarColor = Color.GRAY
        return TextDrawable.builder()
                .beginConfig()
                .bold()
                .endConfig()
                .let {
                    it.buildRound(matrixItem.firstLetterOfDisplayName(), avatarColor)
                }
    }

    private fun openFileSelection() {
        filePickerLauncher.launch("*/*")
    }

    private fun openGallerySelection() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        galleryPickerLauncher.launch(intent)
    }

    private fun handleSelectedFile(uri: Uri?) {
        if (uri != null) {
            // File is selected, display the room list
            roomList.visibility = View.VISIBLE
            showContactsSwitch.visibility = View.VISIBLE
            selectFileButton.visibility = View.GONE
            selectFromGalleryButton.visibility = View.GONE

            // Do something with the selected file URI
            // For example, upload the file to a server or process it further
            // You can use the URI to access the file content or open an input stream to read the file

            // Example: Log the selected file URI
            selectedFileUri = uri
            println("Selected file URI: $uri")
        } else {
            // No file selected, hide the room list
            roomList.visibility = View.GONE
            showContactsSwitch.visibility = View.GONE
            selectFileButton.visibility = View.VISIBLE
            selectFromGalleryButton.visibility = View.VISIBLE
        }
    }

    private fun performLookup(contacts: List<MappedContact>) {
        val session = SessionManager.session

        if (!session.identityService().getUserConsent()) {
            return
        }

        val threePids = contacts.flatMap { contact ->
            contact.emails.map { ThreePid.Email(it.email) } +
                    contact.msisdns.map { ThreePid.Msisdn(it.phoneNumber.replace("+", "").replace(" ", "").replace("(", "").replace(")", "")) }
        }

        // Launch a coroutine to perform the lookup
        coroutineScope.launch {
            try {
                val data = session.identityService().lookUp(threePids)
                mappedContacts = contacts.map { contactModel ->
                    contactModel.copy(
                            emails = contactModel.emails.map { email ->
                                email.copy(
                                        matrixId = data
                                                .firstOrNull { foundThreePid -> foundThreePid.threePid.value == email.email }
                                                ?.matrixId
                                )
                            },
                            msisdns = contactModel.msisdns.map { msisdn ->
                                msisdn.copy(
                                        matrixId = data
                                                .firstOrNull { foundThreePid -> foundThreePid.threePid.value.replace("+", "").replace(" ", "").replace("(", "").replace(")", "") == msisdn.phoneNumber.replace("+", "").replace(" ", "").replace("(", "").replace(")", "") }
                                                ?.matrixId
                                )
                            }
                    )
                }.filter { contactModel ->
                    contactModel.msisdns.any { msisdn ->
                        msisdn.matrixId != null
                    }
                }
                contactAdapter = ContactAdapter(mappedContacts)
                contactList.adapter = contactAdapter
                println(mappedContacts)
            } catch (failure: Throwable) {
                Timber.w(failure, "Unable to perform the lookup")
            }
        }
    }

    private fun setContacts () {
        val allContacts = ContactsDataSource(this).getContacts(
                withEmails = true,
                // Do not handle phone numbers for the moment
                withMsisdn = true
        )

        performLookup(allContacts)
    }

    private fun onRoomClicked(roomSummary: RoomSummary) {
        if (selectedFileUri == null) {
            return
        }

        val roomId = roomSummary.roomId
        val room = SessionManager.session.getRoom(roomId)

        if (room != null) {
            // Filter out the current user from the members list
            val myUserId = SessionManager.session.myUserId
            val senderName = SessionManager.session.userService().getUser(myUserId)?.displayName
            val members = room.membershipService()
                    .getRoomMembers(roomMemberQueryParams { memberships = listOf(Membership.JOIN) })
                    .filter { it.userId != myUserId }

            members.forEach { member ->
                uploadFileToFirebaseStorage(selectedFileUri!!, member.userId, senderName)
            }
        }
    }

    private fun onContactClicked(contact: MappedContact) {
        println(contact)

        val myUserId = SessionManager.session.myUserId
        val senderName = SessionManager.session.userService().getUser(myUserId)?.displayName
        val contactUserId = contact.msisdns.firstOrNull { msisdn -> msisdn.matrixId != null }?.matrixId

        if(contactUserId != null){
            uploadFileToFirebaseStorage(selectedFileUri!!, contactUserId, senderName)
        }
    }

    private fun getFileExtension(uri: Uri): String {
        val contentResolver = contentResolver
        val mimeType = contentResolver.getType(uri)
        return MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: throw IllegalArgumentException("Unsupported file type")
    }

    private fun uploadFileToFirebaseStorage(fileUri: Uri, recipientId: String, senderName: String?) {
        // Show the progress bar
        progressBar.visibility = View.VISIBLE

        val storage = FirebaseStorage.getInstance()
        val storageRef = storage.reference

        val fileName = UUID.randomUUID().toString()
        val fileExtension = getFileExtension(fileUri)
        val filePath = "files/$recipientId/pending/$fileName.$fileExtension"
        val fileRef = storageRef.child(filePath)

        val uploadTask = fileRef.putFile(fileUri)
                .addOnSuccessListener {
                    // Hide the progress bar when the upload is complete
                    progressBar.visibility = View.GONE

                    createFirestoreCloudDocument(filePath, recipientId, senderName)
                }
                .addOnFailureListener { exception ->
                    // Hide the progress bar on failure
                    progressBar.visibility = View.GONE

                    Log.e(TAG, "Error uploading file: ${exception.message}", exception)
                }

        uploadTask.addOnProgressListener { taskSnapshot ->
            val progress = (100.0 * taskSnapshot.bytesTransferred) / taskSnapshot.totalByteCount
            Log.d(TAG, "Upload progress: $progress%")

            // Update the progress bar's progress
            progressBar.progress = progress.toInt()
        }
    }

    private fun createFirestoreCloudDocument(filePath: String, recipientId: String, senderName: String?) {
        val defaultFirestore = FirebaseFirestore.getInstance()

        val data = hashMapOf(
                "filePath" to filePath,
                "recipientId" to recipientId,
                "senderName" to senderName
        )

        defaultFirestore.collection("cloud")
                .add(data)
                .addOnSuccessListener { documentReference ->
                    Log.d(TAG, "Document added successfully! Document ID: ${documentReference.id}")
                    finish()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error adding document: ${exception.message}", exception)
                }
    }
}
