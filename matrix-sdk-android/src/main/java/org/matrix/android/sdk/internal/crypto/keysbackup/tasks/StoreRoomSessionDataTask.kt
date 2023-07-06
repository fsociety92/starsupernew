/*
 * Copyright 2020 The Matrix.org Foundation C.I.C.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.android.sdk.internal.crypto.keysbackup.tasks

import org.matrix.android.sdk.internal.crypto.keysbackup.api.RoomKeysApi
import org.matrix.android.sdk.internal.crypto.keysbackup.model.rest.BackupKeysResult
import org.matrix.android.sdk.internal.crypto.keysbackup.model.rest.KeyBackupData
import org.matrix.android.sdk.internal.network.GlobalErrorReceiver
import org.matrix.android.sdk.internal.network.executeRequest
import org.matrix.android.sdk.internal.task.Task
import javax.inject.Inject

internal interface StoreRoomSessionDataTask : Task<StoreRoomSessionDataTask.Params, BackupKeysResult> {
    data class Params(
            val roomId: String,
            val sessionId: String,
            val version: String,
            val keyBackupData: KeyBackupData
    )
}

internal class DefaultStoreRoomSessionDataTask @Inject constructor(
        private val roomKeysApi: RoomKeysApi,
        private val globalErrorReceiver: GlobalErrorReceiver
) : StoreRoomSessionDataTask {

    override suspend fun execute(params: StoreRoomSessionDataTask.Params): BackupKeysResult {
        return executeRequest(globalErrorReceiver) {
            roomKeysApi.storeRoomSessionData(
                    params.roomId,
                    params.sessionId,
                    params.version,
                    params.keyBackupData
            )
        }
    }
}
