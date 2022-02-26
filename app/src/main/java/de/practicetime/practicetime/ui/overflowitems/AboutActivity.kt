package de.practicetime.practicetime.ui.overflowitems

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.practicetime.practicetime.PracticeTime
import de.practicetime.practicetime.R
import de.practicetime.practicetime.database.entities.*
import de.practicetime.practicetime.ui.goals.updateGoals
import de.practicetime.practicetime.utils.SECONDS_PER_DAY
import de.practicetime.practicetime.utils.SECONDS_PER_HOUR
import de.practicetime.practicetime.utils.getStartOfDay
import de.practicetime.practicetime.utils.getStartOfWeek
import kotlinx.coroutines.launch
import java.lang.Math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        setSupportActionBar(findViewById(R.id.about_toolbar))
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = getString(R.string.app_info)
        }

        findViewById<TextView>(R.id.about_tv_help).setOnClickListener {
            startActivity(Intent(this, HelpActivity::class.java))
        }

        findViewById<TextView>(R.id.about_tv_support).setOnClickListener {
            startActivity(Intent(this, DonationsActivity::class.java))
        }

        findViewById<TextView>(R.id.about_tv_legal).setOnClickListener {
            startActivity(Intent(this, LegalActivity::class.java))
        }

        findViewById<TextView>(R.id.about_tv_licences).setOnClickListener {
            startActivity(Intent(this, LicenseActivity::class.java))
        }

        findViewById<TextView>(R.id.about_appinfo).setOnClickListener {
            startActivity(Intent(this, AboutAppActivity::class.java))
        }

        findViewById<TextView>(R.id.about_text_view_made_with_love).setOnClickListener {
            lifecycleScope.launch {
                val dummyCategories = PracticeTime.categoryDao.insertAndGet(listOf(
                    Category(name="B-Dur", colorIndex = 8),
                    Category(name="Czerny Etude Nr.2", colorIndex = 1),
                    Category(name="Mozart", colorIndex = 3)
                ))

                listOf(
                    Pair(GoalDescriptionWithCategories(
                        description = GoalDescription(
                            type = GoalType.NON_SPECIFIC,
                            repeat = true,
                            periodInPeriodUnits = 1,
                            periodUnit = GoalPeriodUnit.DAY,
                        ),
                        listOf()
                    ), GoalInstance(
                        goalDescriptionId = 1,
                        startTimestamp = getStartOfWeek(-4).toEpochSecond(),
                        periodInSeconds = SECONDS_PER_DAY * 7,
                        target = SECONDS_PER_HOUR,
                    )),
                    Pair(GoalDescriptionWithCategories(
                        description = GoalDescription(
                            type = GoalType.CATEGORY_SPECIFIC,
                            repeat = true,
                            periodInPeriodUnits = 1,
                            periodUnit = GoalPeriodUnit.WEEK,
                        ),
                        listOf(
                            dummyCategories[2]
                        )
                    ), GoalInstance(
                        goalDescriptionId = 2,
                        startTimestamp = getStartOfWeek(-3).toEpochSecond(),
                        periodInSeconds = SECONDS_PER_DAY * 7,
                        target = (SECONDS_PER_HOUR * 2f).roundToInt(),
                    )),
                ).forEach { (description, instance) ->
                    PracticeTime.goalDescriptionDao.insertAndGetGoalDescriptionWithCategories(description)
                    PracticeTime.goalInstanceDao.insert(instance)
                }

                updateGoals()

                val comments = listOf(
                    "Awesome Session!",
                    "Good progress.",
                    "Pretty exhausted... but happy."
                )

                val categoryActiveRanges = listOf(
                    -60L..0L,
                    -55L..-10L,
                    -45L..0L
                )

                val dummySessions = ArrayList<SessionWithSections>().let { sessions ->
                    for (day in -60L..0L) {
                        var startTimeStamp =
                            getStartOfDay(day).toEpochSecond() +
                                    SECONDS_PER_HOUR * drawNumberBetween(13, 18) +
                                    60 * drawNumberBetween(0, 59)
                        sessions.add(SessionWithSections(
                            Session(
                                breakDuration = 60 * drawNumberBetween(5, 20),
                                rating = drawNumberBetween(2, 5),
                                comment = comments.random()
                            ),
                            ArrayList<Section>().let { sections ->
                                dummyCategories.forEach { cat ->
                                    if(categoryActiveRanges[(cat.id-1).toInt() % categoryActiveRanges.size].contains(day)){
                                        val duration = when (cat.name) {
                                            "B-Dur" -> 60 * drawNumberBetween(5, 10)
                                            "Czerny Etude Nr.2" -> 60 * drawNumberBetween(15, 25)
                                            "Mozart" -> 60 * drawNumberBetween(25, 45)
                                            else -> 0
                                        }

                                        sections.add(
                                            Section(
                                                null,
                                                categoryId = cat.id,
                                                duration = duration,
                                                timestamp = startTimeStamp
                                            )
                                        )
                                        startTimeStamp += duration
                                    }
                                }
                                sections.toList()
                            }
                        ))
                    }
                    sessions.toList()
                }

                dummySessions.forEach {
                    val newSessionId = PracticeTime.sessionDao.insertSessionWithSections(it)
                    val latestSession = PracticeTime.sessionDao.getWithSectionsWithCategoriesWithGoals(newSessionId)
                    val goalProgress = PracticeTime.goalDescriptionDao.computeGoalProgressForSession(latestSession)

                    // get all active goal instances at the time of the session
                    PracticeTime.goalInstanceDao.getWithDescriptionsWithCategories(
                        goalDescriptionIds = goalProgress.keys.toList(),
                        checkArchived = false,
                        now = latestSession.sections.first().section.timestamp
                        // store the progress in the database
                    ).onEach { (instance, d) ->
                        goalProgress[d.description.id].also { progress ->
                            if (progress != null && progress > 0) {
                                instance.progress += progress
                                PracticeTime.goalInstanceDao.update(instance)
                            }
                        }
                    }
                }
                Toast.makeText(this@AboutActivity, "Completed!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun drawNumberBetween(start: Int, end: Int): Int {
        return (kotlin.math.abs(Random.nextInt()) % (end - start + 1)) + start
    }
}