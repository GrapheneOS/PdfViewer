package app.grapheneos.pdfviewer.testrules

import android.util.Log
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class RetryRules(private val maxAttempts: Int = 3) : TestRule {

    companion object {
        private const val TAG = "RetryRules"
    }

    init {
        require(maxAttempts >= 1) { "maxAttempts must be positive" }
    }

    override fun apply(base: Statement, desc: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                var lastError: Throwable? = null
                for (attempt in 1..maxAttempts) {
                    try {
                        base.evaluate()
                        if (attempt > 1) {
                            Log.d(TAG, "${desc.displayName}: passed on attempt $attempt/$maxAttempts")
                        }
                        return
                    } catch (t: Throwable) {
                        lastError = t
                        Log.w(TAG, "${desc.displayName}: failed attempt $attempt/$maxAttempts", t)
                    }
                }
                throw lastError!!
            }
        }
    }
}
