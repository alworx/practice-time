/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) 2022 Matthias Emde
 *
 * Parts of this software are licensed under the MIT license
 *
 * Copyright (c) 2022, Javier Carbone, author Matthias Emde
 */

package de.practicetime.practicetime.database.daos

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import de.practicetime.practicetime.database.BaseDao
import de.practicetime.practicetime.database.LibraryItemWithGoalDescriptions
import de.practicetime.practicetime.database.PTDatabase
import de.practicetime.practicetime.database.entities.LibraryItem
import kotlinx.coroutines.flow.Flow
import java.util.*

@Dao
abstract class LibraryItemDao(
    database: PTDatabase
) : BaseDao<LibraryItem>(
    tableName = "library_item",
    database = database
) {

    /**
    *  @Queries
    */

    @Query("SELECT * FROM library_item WHERE archived=0 OR NOT :activeOnly")
    abstract fun get(activeOnly: Boolean = false): Flow<List<LibraryItem>>

    @Query("SELECT * FROM library_item WHERE id=:id")
    abstract suspend fun getById(id: Long): LibraryItem?

    @Query("SELECT * FROM library_item WHERE library_folder_id=:libraryFolderId")
    abstract suspend fun getFromFolder(libraryFolderId: UUID): List<LibraryItem>

    @Transaction
    @Query("SELECT * FROM library_item WHERE id=:id")
    abstract suspend fun getWithGoalDescriptions(id: UUID): LibraryItemWithGoalDescriptions?

    @Transaction
    open suspend fun updateMetronome(
        id: UUID,
        newBpm: Int,
        newBpb: Int,
        newCpb: Int
    ) {
        getById(id)?.let {library_item ->
            library_item.apply {
                bpm = newBpm
                bpb = newBpb
                cpb = newCpb
            }
            update(library_item)
        }
    }
}