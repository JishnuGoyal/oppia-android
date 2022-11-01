package org.oppia.android.app.spotlight

import android.app.Activity
import android.graphics.PointF
import android.graphics.RectF
import androidx.annotation.IdRes

data class SpotlightTargetReference(
  @IdRes val anchorViewId: Int,
  val hint: String = "",
  val shape: SpotlightShape = SpotlightShape.RoundedRectangle,
  val feature: org.oppia.android.app.model.Spotlight.FeatureCase
) {
  fun resolveTarget(activity: Activity): SpotlightTarget {
    val anchorView = checkNotNull(activity.findViewById(anchorViewId)) {
      "Failed to find expected spotlight anchor target for ID: $anchorViewId."
    }
    android.util.Log.e("@@@@@", "resolve to: $anchorView")
    val location = IntArray(2)
    anchorView.getLocationOnScreen(location)

    val anchorLeft = location[0].toFloat()
    val anchorRight = anchorLeft + anchorView.width
    val anchorTop = location[1].toFloat()
    val anchorBottom = anchorTop + anchorView.height
    return SpotlightTarget(
      anchorBounds = RectF(anchorLeft, anchorTop, anchorRight, anchorBottom),
      hint,
      shape,
      feature
    )
  }
}

data class SpotlightTarget(
  val anchorBounds: RectF,
  val hint: String,
  val shape: SpotlightShape,
  val feature: org.oppia.android.app.model.Spotlight.FeatureCase
) {
  val anchorLeft: Float get() = anchorBounds.left
  val anchorTop: Float get() = anchorBounds.top
  val anchorWidth: Float get() = anchorBounds.width()
  val anchorHeight: Float get() = anchorBounds.height()
  val anchorCenterX: Float get() = anchorBounds.centerX()
  val anchorCenterY: Float get() = anchorBounds.centerY()
  val anchorCenter: PointF get() = PointF(anchorBounds.centerX(), anchorBounds.centerY())
}
