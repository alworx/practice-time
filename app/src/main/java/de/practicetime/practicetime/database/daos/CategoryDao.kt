package de.practicetime.practicetime.database.daos

import android.util.Log
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import de.practicetime.practicetime.database.BaseDao
import de.practicetime.practicetime.database.entities.Category
import de.practicetime.practicetime.database.entities.CategoryWithGoalDescriptions

@Dao
abstract class CategoryDao : BaseDao<Category>() {

//    @Transaction
    fun archive(categoryId: Long) : Boolean {
        // to archive a category, fetch it from the database along with associated goals
        getWithGoalDescriptions(categoryId)?.also {
            val (category, goalDescriptions) = it
            // check if there are non-archived goals associated with the selected category
            return if (goalDescriptions.any { d -> !d.archived }) {
                // in this case, we don't allow deletion and return false
                false
            } else {
                category.archived = true
                update(category)
                true
            }
        }
        Log.e("CATEGORY_DAO", "Tried to delete category with invalid id")
        return false
    }

    /**
    *   Queries
    */

    @Query("SELECT * FROM category WHERE id=:id")
    abstract override suspend fun get(id: Long): Category?

    @Query("SELECT * FROM category WHERE id IN (:ids)")
    abstract suspend fun get(ids: List<Long>): List<Category>

    @Query("SELECT * FROM category WHERE archived=0 OR archived = NOT :activeOnly")
    abstract suspend fun getAll(activeOnly: Boolean = false): List<Category>

    @Transaction
    @Query("SELECT * FROM Category WHERE id=:id")
    abstract fun getWithGoalDescriptions(id: Long): CategoryWithGoalDescriptions?
}