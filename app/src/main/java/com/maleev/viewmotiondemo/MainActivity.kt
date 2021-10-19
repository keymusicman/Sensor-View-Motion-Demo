package com.maleev.viewmotiondemo

import android.content.res.Resources
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
  private val viewMotionHelper by lazy { ViewMotionHelper(this, this) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    findViewById<View>(R.id.id1).also { viewMotionHelper.registerView(it, 280.px) }
    findViewById<View>(R.id.id2).also { viewMotionHelper.registerView(it, 100.px) }
  }

  private val Int.px: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()
}
