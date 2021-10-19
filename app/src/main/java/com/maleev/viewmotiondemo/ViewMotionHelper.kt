package com.maleev.viewmotiondemo

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.DecelerateInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent

class ViewMotionHelper(
  context: Context,
  lifecycleOwner: LifecycleOwner
) : SensorEventListener {
  companion object {
    private const val MATRIX_SIZE = 16
    private const val ROTATION_VECTOR_SIZE = 4
    private const val DEFAULT_SAMPLING_PERIOD = 100000
    private const val DEFAULT_DURATION = 300L
    private val DEFAULT_INTERPOLATOR = DecelerateInterpolator()
  }

  private val sensorManager: SensorManager? =
    context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

  init {
    lifecycleOwner.lifecycle.addObserver(SensorLifecycleObserver())
  }

  @Volatile
  private var initialized = false
  private var initialValues = FloatArray(MATRIX_SIZE)
  private val angleChange = FloatArray(MATRIX_SIZE)
  private val rotationMatrix = FloatArray(MATRIX_SIZE)
  private val truncatedRotationVector = FloatArray(ROTATION_VECTOR_SIZE)

  private val viewMotionSpecs = mutableListOf<ViewMotionSpec>()

  fun registerView(
    view: View,
    maxTranslation: Int
  ) {
    viewMotionSpecs.add(ViewMotionSpec(view, maxTranslation))
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (event == null) return

    val rotationVector = getRotationVectorFromSensorEvent(event)

    if (!initialized) {
      initialized = true
      SensorManager.getRotationMatrixFromVector(initialValues, rotationVector)
      return
    }
    SensorManager.getRotationMatrixFromVector(rotationMatrix, rotationVector)

    SensorManager.getAngleChange(angleChange, rotationMatrix, initialValues)

    angleChange.forEachIndexed { index, value ->
      angleChange[index] = radianToFraction(value)
    }

    animate()
  }

  /**
   * Map domain of tilt vector from radian (-PI, PI) to fraction (-1, 1)
   */
  private fun radianToFraction(value: Float): Float {
    return (value / Math.PI)
      .coerceIn(-1.0, 1.0)
      .toFloat()
  }

  private fun animate() {
    viewMotionSpecs.forEach { viewMotionSpec ->
      val view = viewMotionSpec.view

      val xTranslation = -angleChange[2] * viewMotionSpec.maxTranslation
      val yTranslation = angleChange[1] * viewMotionSpec.maxTranslation

      view.animate { translationX(xTranslation) }
      view.animate { translationY(yTranslation) }
    }
  }

  private fun View.animate(builder: ViewPropertyAnimator.() -> Unit) {
    animate()
      .apply {
        duration = DEFAULT_DURATION
        interpolator = DEFAULT_INTERPOLATOR
        builder()
      }
      .start()
  }

  override fun onAccuracyChanged(
    sensor: Sensor,
    p1: Int
  ) {
  }

  private fun getRotationVectorFromSensorEvent(event: SensorEvent): FloatArray {
    return if (event.values.size > ROTATION_VECTOR_SIZE) {
      // On some Samsung devices SensorManager.getRotationMatrixFromVector
      // appears to throw an exception if rotation vector has length > 4.
      // For the purposes of this class the first 4 values of the
      // rotation vector are sufficient (see crbug.com/335298 for details).
      System.arraycopy(event.values, 0, truncatedRotationVector, 0, ROTATION_VECTOR_SIZE)
      truncatedRotationVector
    } else {
      event.values
    }
  }

  private fun registerSensorListener() {
    if (sensorManager == null) return

    sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
      ?.also { sensor ->
        sensorManager.registerListener(this, sensor, DEFAULT_SAMPLING_PERIOD)
      }
  }

  private fun unregisterSensorListener() {
    sensorManager?.unregisterListener(this)
    initialized = false
  }

  private fun removeAllViews() {
    viewMotionSpecs.clear()
  }

  private class ViewMotionSpec(
    val view: View,
    val maxTranslation: Int
  )

  private inner class SensorLifecycleObserver : LifecycleObserver {
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
      registerSensorListener()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun onPause() {
      unregisterSensorListener()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
      removeAllViews()
    }
  }
}