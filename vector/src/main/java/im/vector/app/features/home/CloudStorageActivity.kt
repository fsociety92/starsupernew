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

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import im.vector.app.R
import org.matrix.android.sdk.api.session.Session

object SessionManager {
    lateinit var session: Session
}

class CloudStorageActivity() : AppCompatActivity() {
    private lateinit var fileListRecyclerView: RecyclerView
    private lateinit var fileListAdapter: FileListAdapter
    private lateinit var storage: Storage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        storage = Storage(SessionManager.session)

        setContentView(R.layout.activity_cloud_storage)

        fileListRecyclerView = findViewById(R.id.fileListRecyclerView)
        fileListRecyclerView.layoutManager = LinearLayoutManager(this)
        fileListAdapter = FileListAdapter(this::onPreviewClicked, this::onShareClicked, this::onDeleteFile)
        fileListRecyclerView.adapter = fileListAdapter

        fetchFileList()
    }

    private fun fetchFileList() {
        storage.fetchFileList() { fileList ->
            fileListAdapter.setData(fileList)
        }
    }

    private fun onPreviewClicked(file: String) {
        storage.previewFile(this, file)
        print(file)
    }

    private fun onShareClicked(file: String) {
        storage.shareFile(this, file)
        print(file)
    }

    private fun onDeleteFile(file: String) {
        storage.deleteFile(file)
        fileListAdapter.removeFile(file)
    }
}
