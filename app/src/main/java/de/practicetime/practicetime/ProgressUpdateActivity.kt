package de.practicetime.practicetime

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.Animation
import android.view.animation.Transformation
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import com.google.android.material.button.MaterialButton
import de.practicetime.practicetime.entities.*
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList

const val PROGRESS_UPDATED = 1337

class ProgressUpdateActivity  : AppCompatActivity(R.layout.activity_progress_update) {
    private lateinit var dao: PTDao

    private val progressAdapterData =
        ArrayList<GoalInstanceWithDescriptionWithCategories>()

    private var progressAdapter: GoalAdapter? = null

    private var skipAnimation = false

    private var continueButton : MaterialButton? = null
    private var skipButton : MaterialButton? = null

    override fun onBackPressed() {
        exitActivity()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openDatabase()

        val latestSessionId = intent.extras?.getInt("KEY_SESSION")

        if (latestSessionId != null) {
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    parseSession(latestSessionId)
                } catch (e: Exception) {
                    return@postDelayed
                }
            }, 200)
        } else {
            exitActivity()
        }

        initProgressedGoalList()
    }

    private fun initProgressedGoalList() {
        progressAdapter = GoalAdapter(
            progressAdapterData,
            context = this,
            showEmptyHeader = true,
        )

        findViewById<RecyclerView>(R.id.progessUpdateGoalList).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = progressAdapter
            itemAnimator = CustomAnimator()
        }
    }

    /*************************************************************************
     * Parse latest session and extract goal progress
     *************************************************************************/

    private fun parseSession(sessionId: Int) {
        lifecycleScope.launch {
            val latestSession = dao.getSessionWithSections(sessionId)
            val goalProgress = dao.computeGoalProgressForSession(sessionId)

            // get all active goal instances at the time of the session
            dao.getGoalInstancesWithDescriptionsWithCategories(
                descriptionIds = goalProgress.keys.toList(),
                checkArchived = false,
                now = latestSession.sections.first().timestamp
            // store the progress in the database
            ).onEach { (instance, d) ->
                goalProgress[d.description.id].also { progress ->
                    if (progress != null && progress > 0) {
                        instance.progress += progress
                        dao.updateGoalInstance(instance)

                        // undo the progress locally after updating database for the animation to work
                        instance.progress -= progress
                    }
                }
            // filter out all instances, where the goal is already at 100%
            }.filter {
                it.instance.progress < it.instance.target
            }.also {
                // if no element is progressed which wasn't already at 100%, show ¯\_(ツ)_/¯
                if(it.isEmpty()) {
                    findViewById<LinearLayout>(R.id.shrug).visibility = View.VISIBLE
                    findViewById<RecyclerView>(R.id.progessUpdateGoalList).visibility = View.GONE
                    skipButton?.visibility = View.GONE
                    continueButton?.visibility = View.VISIBLE
                // else start the progress animation
                } else {
                    startProgressAnimation(it, goalProgress)
                }
            }
        }
    }


    /*************************************************************************
     * Progress animation coordination function
     *************************************************************************/

    private fun startProgressAnimation(
        progressedGoalInstancesWithDescriptionsWithCategories
            : List<GoalInstanceWithDescriptionWithCategories>,
        goalProgress: Map<Int, Int>
    ) {

        // prepare the skip and continue button
        continueButton =  findViewById(R.id.progressUpdateLeave)
        skipButton = findViewById(R.id.progressUpdateSkipAnimation)

        continueButton?.setOnClickListener { exitActivity() }
        skipButton?.setOnClickListener { button ->
            skipAnimation = true
            val oldSize = progressAdapterData.size
            progressAdapterData.clear()
            progressAdapter?.notifyItemRangeRemoved(0, oldSize)

            progressedGoalInstancesWithDescriptionsWithCategories.also {
                progressAdapterData.addAll(it)
                progressAdapter?.notifyItemRangeInserted(
                    0,
                    it.size
                )
            }
            button.visibility = View.GONE
            continueButton?.visibility = View.VISIBLE
        }

        val handler = Handler(Looper.getMainLooper())

        // for the animation we want to alternatingly show a new goal and then its progress
        fun showGoal(goalIndex: Int, firstAnimation: Boolean) {
            if(skipAnimation) return

            fun showProgress(progressIndex: Int) {
                if(skipAnimation) return

                progressAdapterData[0].also { (i, d) ->
                    i.progress += goalProgress[d.description.id] ?: 0
                }
                progressAdapter?.notifyItemChanged(1, PROGRESS_UPDATED)

                // only continue showing goals, as long as there are more
                if (progressIndex+1 < progressedGoalInstancesWithDescriptionsWithCategories.size) {
                    handler.postDelayed({
                        showGoal(progressIndex+1, false)
                    }, 1500L)
                // when animation is complete change the visibility of skip and continue button
                } else {
                    skipButton?.visibility = View.GONE
                    continueButton?.visibility = View.VISIBLE
                }
            }

            // skip all goals, where progress is already at 100%
            if(progressedGoalInstancesWithDescriptionsWithCategories[goalIndex].instance.let {
                it.progress >= it.target
            }) {
                // before skipping, check if there actually is another goal and if not show continue
                if(goalIndex+1 < progressedGoalInstancesWithDescriptionsWithCategories.size) {
                    showGoal(goalIndex + 1, firstAnimation)
                } else {
                    skipButton?.visibility = View.GONE
                    continueButton?.visibility = View.VISIBLE
                }
                return
            }

            progressAdapterData.add(0, progressedGoalInstancesWithDescriptionsWithCategories[goalIndex])
            progressAdapter?.notifyItemInserted(1)

            // the progress animation for the first element should
            // start earlier since there is now fade-in beforehand
            handler.postDelayed(
                { showProgress(goalIndex) },
                if (firstAnimation) 500L else 1000L
            )
        }

        // start the progress animation in reverse order with the last goal
        handler.post { showGoal(0, true) }
    }

    /*************************************************************************
     * Utility functions
     *************************************************************************/

    private fun openDatabase() {
        val db = Room.databaseBuilder(
            this,
            PTDatabase::class.java, "pt-database"
        ).build()
        dao = db.ptDao
    }

    private fun exitActivity() {
        // go back to MainActivity, make new intent so MainActivity gets reloaded and shows new session
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    /*************************************************************************
     * Progress bar animation
     *************************************************************************/

    private class ProgressBarAnimation(
        private val progressBar: ProgressBar,
        private val progressIndicator: TextView,
        private val from: Int,
        private val to: Int
    ) : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: Transformation?) {
            super.applyTransformation(interpolatedTime, t)
            var progress = (from + (to - from) * interpolatedTime).toInt()

            progressBar.progress = progress

            // correct for the fps scaling factor
            progress /= 60

            // progress Indicator Text
            val cappedProgress = minOf(progressBar.max / 60, progress)
            val progressHours = cappedProgress / 3600
            val progressMinutes = cappedProgress % 3600 / 60
            when {
                progressHours > 0 -> progressIndicator.text = String.format("%02d:%02d", progressHours, progressMinutes)
                progressMinutes > 0 -> progressIndicator.text = String.format("%d min", progressMinutes)
                else -> progressIndicator.text = "< 1 min"
            }
        }
    }

    /*************************************************************************
     * Custom animator
     *************************************************************************/

    private class CustomAnimator() : DefaultItemAnimator() {
        // change the duration of the fade in and move animation
        override fun getAddDuration() = 250L
        override fun getMoveDuration() = 500L

        override fun animateChange(
            oldHolder: RecyclerView.ViewHolder,
            newHolder: RecyclerView.ViewHolder,
            preInfo: ItemHolderInfo,
            postInfo: ItemHolderInfo
        ): Boolean {

            if (preInfo is GoalItemHolderInfo) {
                val progressBar = (newHolder as GoalAdapter.ViewHolder).progressBarView
                val progressIndicator = newHolder.goalProgressIndicatorView
                // multiply by 60 to grantee at least 60 fps
                progressBar.max *= 60
                val animator = ProgressBarAnimation(
                    progressBar,
                    progressIndicator,
                    preInfo.progress * 60,
                    (progressBar.progress * 60).let { if (it < progressBar.max) it else progressBar.max}
                )
                animator.duration = 1300
                progressBar.startAnimation(animator)
                return true
            }

            return super.animateChange(oldHolder, newHolder, preInfo, postInfo)
        }

        override fun canReuseUpdatedViewHolder(
            viewHolder: RecyclerView.ViewHolder, payloads: MutableList<Any>
        ) = true

        override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder) = true


        override fun recordPreLayoutInformation(
            state: RecyclerView.State,
            viewHolder: RecyclerView.ViewHolder,
            changeFlags: Int,
            payloads: MutableList<Any>
        ): ItemHolderInfo {

            if (changeFlags == FLAG_CHANGED) {
                if (payloads[0] as? Int == PROGRESS_UPDATED) {
                    //Get the info from the viewHolder and save it to GoalItemHolderInfo
                    return GoalItemHolderInfo(
                        (viewHolder as GoalAdapter.ViewHolder).progressBarView.progress,
                    )
                }
            }
            return super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads)
        }

        class GoalItemHolderInfo(val progress: Int) : ItemHolderInfo()
    }
}


