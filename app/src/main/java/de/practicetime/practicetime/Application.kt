package de.practicetime.practicetime

import android.app.Application
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PracticeTime : Application() {
    val executorService = Executors.newFixedThreadPool(4)
}