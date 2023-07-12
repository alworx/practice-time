/*
 * This software is licensed under the MIT license
 *
 * Copyright (c) 2022, Javier Carbone, author Matthias Emde
 */

package de.practicetime.practicetime.database.daos

import android.util.Log
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import de.practicetime.practicetime.database.BaseDao
import de.practicetime.practicetime.database.entities.*

@Dao
abstract class LibraryItemDao : BaseDao<LibraryItem>(tableName = "library_item") {

    /**
     * @Delete / archive
     */

    @Transaction
    open suspend fun archive(libraryItemId: Long) : Boolean {
        // to archive a libraryItem, fetch it from the database along with associated goals
        getWithGoalDescriptions(libraryItemId)?.also {
            val (libraryItem, goalDescriptions) = it
            // check if there are non-archived goals associated with the selected libraryItem
            return if (goalDescriptions.any { d -> !d.archived }) {
                // in this case, we don't allow deletion and return false
                false
            } else {
                libraryItem.archived = true
                update(libraryItem)
                true
            }
        }
        Log.e("CATEGORY_DAO", "Tried to delete libraryItem with invalid id")
        return false
    }


    /**
    *  @Queries
    */

    @Query("SELECT * FROM library_item WHERE archived=0 OR NOT :activeOnly")
    abstract suspend fun get(activeOnly: Boolean = false): List<LibraryItem>

    @Query("SELECT * FROM library_item WHERE id=:id")
    abstract suspend fun getById(id: Long): LibraryItem?

    @Query("SELECT * FROM library_item WHERE library_folder_id=:libraryFolderId")
    abstract suspend fun getFromFolder(libraryFolderId: Long): List<LibraryItem>

    @Transaction
    @Query("SELECT * FROM library_item WHERE id=:id")
    abstract suspend fun getWithGoalDescriptions(id: Long): LibraryItemWithGoalDescriptions?

    @Transaction
    open suspend fun updateMetronome(
        id: Long,
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