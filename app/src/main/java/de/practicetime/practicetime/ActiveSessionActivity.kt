package de.practicetime.practicetime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.room.Room
import androidx.transition.Fade
import androidx.transition.TransitionManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import de.practicetime.practicetime.entities.Category
import de.practicetime.practicetime.entities.PracticeSection
import de.practicetime.practicetime.entities.PracticeSession
import kotlinx.coroutines.launch
import java.util.*
import kotlin.collections.ArrayList





class ActiveSessionActivity : AppCompatActivity() {

    private var dao: PTDao? = null
    private var activeCategories: List<Category>? = listOf()
    private lateinit var sectionsAdapter: ArrayAdapter<String>
    private var listItems = ArrayList<String>()
    private lateinit var mService: SessionForegroundService
    private var mBound: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_session)

        openDatabase()

        practiceTimer()

        // initialize adapter and recyclerView for showing category buttons from database
        initCategoryList()

        initEndSessionDialog()

        val btnPause = findViewById<MaterialButton>(R.id.bottom_pause)
        btnPause.setOnClickListener {
            if (mService.paused) {
                mService.paused = false
                mService.pauseDuration = 0
            } else {
                mService.paused = true
            }
            // adapt UI to changes
            adaptUIPausedState(mService.paused)
        }

        // init SectionListView Adapter
        val sectionsList = findViewById<ListView>(R.id.currentSections)
        sectionsAdapter =
            ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, listItems)
        sectionsList.adapter = sectionsAdapter

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }
    }

    private fun initCategoryList() {
        val categories = ArrayList<Category>()
        val categoryAdapter = CategoryAdapter(categories, ::categoryPressed)

        val categoryList = findViewById<RecyclerView>(R.id.categoryList)
        categoryList.layoutManager = GridLayoutManager(this, 3, GridLayoutManager.HORIZONTAL, false)
        categoryList.adapter = categoryAdapter

        lifecycleScope.launch {
            activeCategories = dao?.getActiveCategories()
            if (activeCategories != null) {
                categories.addAll(activeCategories!!)
            }
            // notifyDataSetChanged necessary here since all items might have changed
            categoryAdapter.notifyDataSetChanged()
        }
    }

    // the routine for handling presses to category buttons
    private fun categoryPressed(categoryView: View) {
        // get the category id from the view tag and calculate current timestamp
        val categoryId = categoryView.tag as Int

        if (!mService.sessionActive) {   // session starts now
            //start the service so that timer starts
            Intent(this, SessionForegroundService::class.java).also {
                startService(it)
            }
            setPauseStopBtnVisibility(true)
        } else {
            mService.endSection()
        }
        // start a new section for the chosen category
        mService.startNewSection(categoryId)
        // immediately update list
        fillSectionList()
    }

    /**
     * This function adapts all UI elements which change by pausing depending on paused
     * It can be called whenever required to sync the UI with the data in the service
     */
    private fun adaptUIPausedState(paused: Boolean) {
        if (paused) {
            // swap pause icon with play icon
            findViewById<MaterialButton>(R.id.bottom_pause).apply {
                setIconResource(R.drawable.ic_play)
            }
            // show the fab
            findViewById<ExtendedFloatingActionButton>(R.id.fab_info_popup).apply {
                show()
            }
            showOverlay()
        } else {
            // swap play icon with pause icon
            findViewById<MaterialButton>(R.id.bottom_pause).apply {
                setIconResource(R.drawable.ic_pause)
            }
            // hide the fab
            findViewById<ExtendedFloatingActionButton>(R.id.fab_info_popup).apply {
                hide()
                // reset text to zero so that on next pause old time is visible for a short moment
                text = "Pause: 00:00:00"
            }
            hideOverlay()
        }
    }

    /**
     * Shows a translucent overlay above the screen (TextView) with a fade animation and adapts the
     * rest of the UI to these changes
     */
    private fun showOverlay() {
        val transition = Fade().apply {
            duration = 600
            addTarget(R.id.tv_overlay_pause)
        }
        TransitionManager.beginDelayedTransition(
            findViewById(R.id.coordinator_layout_active_session),
            transition
        )
        findViewById<TextView>(R.id.tv_overlay_pause).visibility = View.VISIBLE

        // make up button white
        findViewById<ImageButton>(R.id.btn_back).setColorFilter(Color.WHITE)
    }

    private fun hideOverlay() {
        val transition = Fade().apply {
            duration = 600
            addTarget(R.id.tv_overlay_pause)
        }
        TransitionManager.beginDelayedTransition(
            findViewById(R.id.coordinator_layout_active_session),
            transition
        )
        findViewById<TextView>(R.id.tv_overlay_pause).visibility = View.GONE

        // make up button subtle grey or subtle white, according to theme
        val typedValue = TypedValue()
        theme.resolveAttribute(R.attr.colorOnSurface, typedValue, true)
        val color = typedValue.data
        findViewById<ImageButton>(R.id.btn_back).setColorFilter(color)
    }


    private fun finishSession(rating: Int, comment: String?) {
        // finish the final section
        mService.endSection()

        // get total break duration
        var totalBreakDuration = 0
        mService.sectionBuffer.forEach {
            totalBreakDuration += it.second
        }

        // TODO: Check if comment is empty -> insert null
        val newSession = PracticeSession(
            0,      // id=0 means not assigned, autoGenerate=true will do it for us
            totalBreakDuration,
            rating,
            comment,
            1
        )

        lifecycleScope.launch {
            // create a new session row and save its id
            val sessionId = dao?.insertSession(newSession)

            // add the new sessionId to every section in the section buffer
            for (section in mService.sectionBuffer) {
                section.first.practice_session_id = sessionId?.toInt()
                // update section durations to exclude break durations
                section.first.duration = section.first.duration?.minus(section.second)
                // and insert them into the database
                dao?.insertSection(section.first)
            }

            // reset section buffer and session status
            mService.sectionBuffer.clear()
        }

        // stop the service
        Intent(this, SessionForegroundService::class.java).also {
            stopService(it)
        }
        // go back to MainActivity, make new intent so MainActivity gets reloaded and shows new session
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
    }

    private fun initEndSessionDialog() {
        // instantiate the builder for the alert dialog
        val endSessionDialogBuilder = AlertDialog.Builder(this)
        val inflater = this.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_view_end_session, null)

        val dialogRatingBar = dialogView.findViewById<RatingBar>(R.id.dialogRatingBar)
        val dialogBackButton = dialogView.findViewById<ImageButton>(R.id.btn_back_alertdialog)
        val dialogComment = dialogView.findViewById<EditText>(R.id.dialogComment)

        // Dialog Setup
        endSessionDialogBuilder.apply {
            setView(dialogView)
            setCancelable(false)
            setPositiveButton(R.string.endSessionAlertOk) { _, _ ->
                val rating = dialogRatingBar.rating.toInt()
                finishSession(rating, dialogComment.text.toString())
            }
            setNegativeButton(R.string.discard_session) { _, _ ->
                // clear the sectionBuffer so that runnable dies
                mService.sectionBuffer.clear()
                // stop the service
                Intent(this@ActiveSessionActivity, SessionForegroundService::class.java).also {
                    stopService(it)
                }
                // terminate and go back to MainActivity
                finish()
            }
        }
        val endSessionDialog: AlertDialog = endSessionDialogBuilder.create()

        // stop session button functionality
        findViewById<MaterialButton>(R.id.bottom_stop).setOnClickListener {
            // show the end session dialog
            endSessionDialog.show()
            endSessionDialog.also {
                val positiveButton = it.getButton(AlertDialog.BUTTON_POSITIVE)
                dialogRatingBar.setOnRatingBarChangeListener { _, rating, _ ->
                    positiveButton.isEnabled = rating.toInt() > 0
                }
                dialogBackButton.setOnClickListener {
                    if (!mService.paused) {
                        hideOverlay()
                    }
                    endSessionDialog.cancel()
                }
            }
            showOverlay()
        }
    }

    private fun openDatabase() {
        val db = Room.databaseBuilder(
            this,
            PTDatabase::class.java, "pt-database"
        ).build()
        dao = db.ptDao
    }

    /**
     * calculates total Duration (including pauses) of a section
     */
    private fun getDuration(section: PracticeSection): Int {
        val now = Date().time / 1000L
        return (now - section.timestamp).toInt()
    }

    /**
     * TODO should be replaced by functions triggered from the service rather than polling every 100ms
     */
    private fun practiceTimer() {
        // creates a new Handler
        Handler(Looper.getMainLooper()).also {
            // the post() method executes immediately
            it.post(object : Runnable {
                override fun run() {
                    if (mBound) {
                        updateViews()
                    }
                    // post the code again with a delay of 100 milliseconds so that ui is more responsive
                    it.postDelayed(this, 1000)
                }
            })
        }
    }

    private fun setPauseStopBtnVisibility(sessionActive: Boolean) {
        if (mBound) {
            if (sessionActive) {
                findViewById<MaterialButton>(R.id.bottom_pause).visibility = View.VISIBLE
                findViewById<MaterialButton>(R.id.bottom_stop).visibility = View.VISIBLE
                findViewById<MaterialButton>(R.id.bottom_metronome).apply {
                    text = ""       // remove text from Button
                    iconPadding = 0 // center icon
                }
                findViewById<MaterialButton>(R.id.bottom_record).apply {
                    text = ""       // remove text from Button
                    iconPadding = 0 // center icon
                }
            } else {
                findViewById<MaterialButton>(R.id.bottom_pause).visibility = View.GONE
                findViewById<MaterialButton>(R.id.bottom_stop).visibility = View.GONE
            }
            adaptUIPausedState(mService.paused)
        }

    }

    private fun updateViews() {
        val practiceTimeView = findViewById<TextView>(R.id.practiceTimer)
        if (mService.sessionActive) {
            val fabInfoPause = findViewById<ExtendedFloatingActionButton>(R.id.fab_info_popup)
            // load the current section from the sectionBuffer
            if (mService.paused) {
                // display pause duration on the fab, but only time after pause was activated
                fabInfoPause.text = "Pause: %02d:%02d:%02d".format(
                    mService.pauseDuration / 3600,
                    mService.pauseDuration % 3600 / 60,
                    mService.pauseDuration % 60
                )
            }
            practiceTimeView.text = "%02d:%02d:%02d".format(
                mService.totalPracticeDuration / 3600,
                mService.totalPracticeDuration % 3600 / 60,
                mService.totalPracticeDuration % 60
            )

            fillSectionList()
        } else {
            practiceTimeView.text = "00:00:00"
        }
    }

    /**
     * TODO ugly code, migrate to Recyclerview
     */
    private fun fillSectionList() {
        if (activeCategories != null) {
            // show all sections in listview
            listItems.clear()
            for (n in mService.sectionBuffer.size - 1 downTo 0) {
                var duration =
                    mService.sectionBuffer[n].first.duration?.minus(mService.sectionBuffer[n].second)
                if (duration == null) {
                    duration =
                        getDuration(mService.sectionBuffer[n].first).minus(mService.sectionBuffer[n].second)
                }
                listItems.add(
                    "${activeCategories?.get(mService.sectionBuffer[n].first.category_id - 1)?.name} " +
                            "\t\t\t\t\t${duration}s"
                )
            }
            sectionsAdapter.notifyDataSetChanged()
        }
    }

    /**
     *  Adapter for the Category selection button grid.
     */

    private inner class CategoryAdapter(
        private val dataSet: ArrayList<Category>,
        private val callback: View.OnClickListener
    ) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val button: MaterialButton = view.findViewById(R.id.button)

            init {
                // Define click listener for the ViewHolder's View.
                button.setOnClickListener(callback)
            }
        }

        // Create new views (invoked by the layout manager)
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            // Create a new view, which defines the UI of the list item
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.view_category_item, viewGroup, false)

            return ViewHolder(view)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            // Get element from your dataset at this position
            val category = dataSet[position]

            // store the id of the category on the button
            viewHolder.button.tag = category.id

            // archived categories should not be displayed
            if (category.archived) {
                viewHolder.button.visibility = View.GONE
            }

            // contents of the view with that element
            viewHolder.button.text = category.name
            viewHolder.button.setBackgroundColor(category.color)

            // TODO set right margin for last 3 elements programmatically
        }

        // Return the size of your dataset (invoked by the layout manager)
        override fun getItemCount() = dataSet.size
    }

    override fun onStart() {
        super.onStart()
        // Bind to SessionForegroundService
        Intent(this, SessionForegroundService::class.java).also { intent ->
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onStop() {
        super.onStop()
        unbindService(connection)
        mBound = false
    }

    override fun onBackPressed() {
        super.onBackPressed()
        Log.d("TAH", "OnBack")
        overridePendingTransition(0, R.anim.slide_out_down)
    }

    /** Defines callbacks for service binding, passed to bindService()  */
    private val connection = object : ServiceConnection {

        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            val binder = service as SessionForegroundService.LocalBinder
            mService = binder.getService()
            mBound = true
            // sync UI with service data
            updateViews()
            adaptUIPausedState(mService.paused)
            setPauseStopBtnVisibility(mService.sessionActive)
            lifecycleScope.launch {
                activeCategories = dao?.getActiveCategories()
            }
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            mBound = false
        }
    }

}