package de.practicetime.practicetime.database.entities

import androidx.room.Relation
import androidx.room.Embedded
import androidx.room.Junction


data class SessionWithSections(
    @Embedded val session: PracticeSession,
    @Relation(
        parentColumn = "id",
        entityColumn = "practice_session_id"
    )
    val sections: List<PracticeSection>
)

data class SectionWithCategory(
    @Embedded val section: PracticeSection,
    @Relation(
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: Category
)

data class SessionWithSectionsWithCategories(
    @Embedded val session: PracticeSession,
    @Relation(
        entity = PracticeSection::class,
        parentColumn = "id",
        entityColumn = "practice_session_id"
    )
    val sections: List<SectionWithCategory>
)

data class GoalDescriptionWithCategories(
    @Embedded val description: GoalDescription,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            GoalDescriptionCategoryCrossRef::class,
            parentColumn = "goal_description_id",
            entityColumn = "category_id"
        )
    )
    val categories: List<Category>
)

data class CategoryWithGoalDescriptions(
    @Embedded val category: Category,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            GoalDescriptionCategoryCrossRef::class,
            parentColumn = "category_id",
            entityColumn = "goal_description_id"
        )
    )
    val descriptions: List<GoalDescription>
)

data class CategoryWithGoalDescriptionsWithCategories(
    @Embedded val category: Category,
    @Relation(
        entity = GoalDescription::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            GoalDescriptionCategoryCrossRef::class,
            parentColumn = "category_id",
            entityColumn = "goal_description_id"
        )
    )
    val descriptions: List<GoalDescriptionWithCategories>
)

data class SectionWithCategoryWithGoalDescriptions(
    @Embedded val section: PracticeSection,
    @Relation(
        entity = Category::class,
        parentColumn = "category_id",
        entityColumn = "id"
    )
    val category: CategoryWithGoalDescriptions
)

data class SessionWithSectionsWithCategoriesWithGoalDescriptions(
    @Embedded val session: PracticeSession,
    @Relation(
        entity = PracticeSection::class,
        parentColumn = "id",
        entityColumn = "practice_session_id"
    )
    val sections: List<SectionWithCategoryWithGoalDescriptions>
)

data class GoalInstanceWithDescription(
    @Embedded val instance: GoalInstance,
    @Relation(
        parentColumn = "goal_description_id",
        entityColumn = "id"
    )
    val description: GoalDescription
)

//data class GoalDescriptionWithInstances(
//    @Embedded val description: GoalDescription,
//    @Relation(
//        parentColumn = "id",
//        entityColumn = "goalDescriptionId"
//    )
//    val instances: List<GoalInstance>
//)

data class GoalInstanceWithDescriptionWithCategories(
    @Embedded val instance: GoalInstance,
    @Relation(
        entity = GoalDescription::class,
        parentColumn = "goal_description_id",
        entityColumn = "id"
    )
    val description: GoalDescriptionWithCategories
)