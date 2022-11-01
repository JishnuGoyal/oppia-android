package org.oppia.android.app.spotlight

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.takusemba.spotlight.OnSpotlightListener
import com.takusemba.spotlight.OnTargetListener
import com.takusemba.spotlight.Spotlight
import com.takusemba.spotlight.Target
import com.takusemba.spotlight.shape.Circle
import com.takusemba.spotlight.shape.RoundedRectangle
import com.takusemba.spotlight.shape.Shape
import org.oppia.android.R
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.SpotlightViewState
import org.oppia.android.app.onboarding.SpotlightNavigationListener
import org.oppia.android.databinding.BottomLeftOverlayBinding
import org.oppia.android.databinding.BottomRightOverlayBinding
import org.oppia.android.databinding.TopLeftOverlayBinding
import org.oppia.android.databinding.TopRightOverlayBinding
import org.oppia.android.domain.spotlight.SpotlightStateController
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProviders.Companion.toLiveData
import java.util.Locale
import javax.inject.Inject
import org.oppia.android.app.fragment.FragmentComponentImpl
import org.oppia.android.app.fragment.InjectableFragment
import org.oppia.android.util.accessibility.AccessibilityService

class SpotlightFragment : InjectableFragment(), SpotlightNavigationListener {
  // TODO: Move these to a presenter.
  @Inject lateinit var activity: AppCompatActivity
  @Inject lateinit var spotlightStateController: SpotlightStateController
  @Inject lateinit var accessibilityServiceImpl: AccessibilityService

  private val targetList = mutableListOf<Target>()
  private lateinit var spotlightTargetList: List<SpotlightTargetReference>
  private lateinit var spotlight: Spotlight
  private var screenHeight: Int = 0
  private var screenWidth: Int = 0
  private lateinit var anchorPosition: AnchorPosition
  private lateinit var overlayBinding: Any
  private var internalProfileId: Int = -1
  private var isRTL = false

  private fun calculateScreenSize() {
    val displayMetrics = DisplayMetrics()
    activity.windowManager.defaultDisplay.getMetrics(displayMetrics)

    screenHeight = displayMetrics.heightPixels
    screenWidth = displayMetrics.widthPixels
  }

  fun initialiseTargetList(spotlightTargets: List<SpotlightTargetReference>, profileId: Int) {
    android.util.Log.e("@@@@@", "want to show: ${spotlightTargets.first().anchorViewId}")
    spotlightTargetList = spotlightTargets
    internalProfileId = profileId
  }

  // since this fragment does not have any view to inflate yet, all the tasks should be done here.
  override fun onAttach(context: Context) {
    super.onAttach(context)
    (fragmentComponent as FragmentComponentImpl).inject(this)
    android.util.Log.e("@@@@@", "onAttach, screenreader on: ${accessibilityServiceImpl.isScreenReaderEnabled()}")

    if (accessibilityServiceImpl.isScreenReaderEnabled()) {
      activity.supportFragmentManager.beginTransaction().remove(this)
    } else {
      calculateScreenSize()
      checkIsRTL()
      android.util.Log.e("@@@@@", "targets: ${spotlightTargetList.size}")
      spotlightTargetList.forEachIndexed { _, spotlightTarget ->
        checkSpotlightViewState(spotlightTarget)
      }
    }
  }

  private fun checkSpotlightViewState(spotlightTargetReference: SpotlightTargetReference) {
    var counter = 0
    val profileId = ProfileId.newBuilder()
      .setInternalId(internalProfileId)
      .build()

    val featureViewStateLiveData =
      spotlightStateController.retrieveSpotlightViewState(
        profileId,
        spotlightTargetReference.feature
      ).toLiveData()

    // use activity as observer because this fragment's view hasn't been created yet.
    android.util.Log.e("@@@@@", "register with $featureViewStateLiveData")
    featureViewStateLiveData.observe(
      activity,
      object : Observer<AsyncResult<SpotlightViewState>> {
        override fun onChanged(it: AsyncResult<SpotlightViewState>?) {
          android.util.Log.e("@@@@@", "receive result: $it")
          if (it is AsyncResult.Success) {
            val viewState = (it.value)
            android.util.Log.e("@@@@@", "attempt to show: ${spotlightTargetReference.anchorViewId} (state: $viewState)")
            if (viewState == SpotlightViewState.SPOTLIGHT_SEEN) {
              return
            } else if (viewState == SpotlightViewState.SPOTLIGHT_NOT_SEEN) {
              createTarget(spotlightTargetReference.resolveTarget(activity))
              counter++
              if (counter == spotlightTargetList.size) {
                startSpotlight()
              }
              featureViewStateLiveData.removeObserver(this)
            }
          }
        }
      }
    )
  }

  private fun createTarget(spotlightTarget: SpotlightTarget) {
    val target = Target.Builder()
      .setAnchor(spotlightTarget.anchorCenter)
      .setShape(getShape(spotlightTarget))
      .setOverlay(requestOverlayResource(spotlightTarget))
      .setOnTargetListener(object : OnTargetListener {
        override fun onStarted() {
        }

        override fun onEnded() {
          val profileId = ProfileId.newBuilder()
            .setInternalId(internalProfileId)
            .build()
          spotlightStateController.markSpotlightViewed(
            profileId,
            spotlightTarget.feature
          )
        }
      })
      .build()

    targetList.add(target)
  }

  private fun startSpotlight() {
    spotlight = Spotlight.Builder(activity)
      .setTargets(targetList)
      .setBackgroundColorRes(R.color.spotlightBackground)
      .setDuration(1000L)
      .setAnimation(DecelerateInterpolator(2f))
      .setOnSpotlightListener(object : OnSpotlightListener {
        override fun onStarted() {
        }

        override fun onEnded() {
        }
      })
      .build()

    spotlight.start()
  }

  private fun getShape(spotlightTarget: SpotlightTarget): Shape {
    return when (spotlightTarget.shape) {
      SpotlightShape.RoundedRectangle -> {
        RoundedRectangle(
          spotlightTarget.anchorHeight,
          spotlightTarget.anchorWidth,
          radius = 24f
        )
      }
      SpotlightShape.Circle -> {
        return if (spotlightTarget.anchorHeight > spotlightTarget.anchorWidth) {
          Circle(spotlightTarget.anchorHeight / 2)
        } else {
          Circle(spotlightTarget.anchorWidth / 2)
        }
      }
    }
  }

  private fun checkIsRTL() {
    val locale = Locale.getDefault()
    val directionality: Byte = Character.getDirectionality(locale.displayName[0].toInt())
    isRTL = directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
      directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
  }

  private fun getArrowWidth(): Float {
    return this.resources.getDimension(R.dimen.arrow_width)
  }

  private fun getScreenCenterY(): Int {
    return screenHeight / 2
  }

  private fun getScreenCenterX(): Int {
    return screenWidth / 2
  }

  private fun calculateAnchorPosition(spotlightTarget: SpotlightTarget) {
    anchorPosition = if (spotlightTarget.anchorCenterX > getScreenCenterX()) {
      if (spotlightTarget.anchorCenterY > getScreenCenterY()) {
        AnchorPosition.BottomRight
      } else {
        AnchorPosition.TopRight
      }
    } else if (spotlightTarget.anchorCenterY > getScreenCenterY()) {
      AnchorPosition.BottomLeft
    } else {
      AnchorPosition.TopLeft
    }
  }

  private fun requestOverlayResource(spotlightTarget: SpotlightTarget): View {
    calculateAnchorPosition(spotlightTarget)

    return when (anchorPosition) {
      AnchorPosition.TopLeft -> {
        if (isRTL) {
          configureTopRightOverlay(spotlightTarget)
        } else {
          configureTopLeftOverlay(spotlightTarget)
        }
      }
      AnchorPosition.TopRight -> {
        configureTopRightOverlay(spotlightTarget)
      }
      AnchorPosition.BottomRight -> {
        if (isRTL) {
          configureBottomLeftOverlay(spotlightTarget)
        } else {
          configureBottomRightOverlay(spotlightTarget)
        }
      }
      AnchorPosition.BottomLeft -> {
        if (isRTL) {
          configureBottomRightOverlay(spotlightTarget)
        } else {
          configureBottomLeftOverlay(spotlightTarget)
        }
      }
    }
  }

  private sealed class AnchorPosition {
    object TopLeft : AnchorPosition()
    object TopRight : AnchorPosition()
    object BottomLeft : AnchorPosition()
    object BottomRight : AnchorPosition()
  }

  override fun clickOnDismiss() {
    spotlight.finish()
  }

  override fun clickOnNextTip() {
    spotlight.next()
  }

  private fun configureBottomLeftOverlay(spotlightTarget: SpotlightTarget): View {
    overlayBinding = BottomLeftOverlayBinding.inflate(this.layoutInflater)
    (overlayBinding as BottomLeftOverlayBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as BottomLeftOverlayBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as BottomLeftOverlayBinding).arrow.layoutParams
      as ViewGroup.MarginLayoutParams
    if (isRTL) {
      arrowParams.setMargins(
        10.dp,
        (spotlightTarget.anchorTop.toInt() - spotlightTarget.anchorHeight - 5.dp).toInt(),
        screenWidth - spotlightTarget.anchorLeft.toInt(),
        10.dp
      )
    } else {
      arrowParams.setMargins(
        spotlightTarget.anchorLeft.toInt(),
        (spotlightTarget.anchorTop.toInt() - spotlightTarget.anchorHeight - 5.dp).toInt(),
        10.dp,
        10.dp
      )
    }
    (overlayBinding as BottomLeftOverlayBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as BottomLeftOverlayBinding).root
  }

  private fun configureBottomRightOverlay(spotlightTarget: SpotlightTarget): View {
    overlayBinding = BottomRightOverlayBinding.inflate(this.layoutInflater)
    (overlayBinding as BottomRightOverlayBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as BottomRightOverlayBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as BottomRightOverlayBinding).arrow.layoutParams
      as ViewGroup.MarginLayoutParams
    if (isRTL) {
      arrowParams.setMargins(
        10.dp,
        (spotlightTarget.anchorTop.toInt() - spotlightTarget.anchorHeight - 5.dp).toInt(),
        screenWidth -
          (spotlightTarget.anchorLeft + spotlightTarget.anchorWidth - getArrowWidth()).toInt(),
        10.dp
      )
    } else {
      arrowParams.setMargins(
        (spotlightTarget.anchorLeft + spotlightTarget.anchorWidth - getArrowWidth()).toInt(),
        (spotlightTarget.anchorTop.toInt() - spotlightTarget.anchorHeight - 5.dp).toInt(),
        10.dp,
        10.dp
      )
    }
    (overlayBinding as BottomRightOverlayBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as BottomRightOverlayBinding).root
  }

  private fun configureTopRightOverlay(spotlightTarget: SpotlightTarget): View {
    overlayBinding = TopRightOverlayBinding.inflate(layoutInflater)
    (overlayBinding as TopRightOverlayBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as TopRightOverlayBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as TopRightOverlayBinding).arrow.layoutParams
      as ViewGroup.MarginLayoutParams
    if (isRTL) {
      arrowParams.setMargins(
        10.dp,
        (spotlightTarget.anchorTop + spotlightTarget.anchorHeight + 5.dp).toInt(),
        screenWidth -
          (spotlightTarget.anchorLeft + spotlightTarget.anchorWidth - getArrowWidth()).toInt(),
        10.dp
      )
    } else {
      arrowParams.setMargins(
        (spotlightTarget.anchorLeft + spotlightTarget.anchorWidth - getArrowWidth() + 5.dp).toInt(),
        (spotlightTarget.anchorTop + spotlightTarget.anchorHeight).toInt(),
        10.dp,
        10.dp
      )
    }
    (overlayBinding as TopRightOverlayBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as TopRightOverlayBinding).root
  }

  private fun configureTopLeftOverlay(spotlightTarget: SpotlightTarget): View {
    overlayBinding = TopLeftOverlayBinding.inflate(this.layoutInflater)
    (overlayBinding as TopLeftOverlayBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as TopLeftOverlayBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as TopLeftOverlayBinding).arrow.layoutParams
      as ViewGroup.MarginLayoutParams
    if (isRTL) {
      arrowParams.setMargins(
        10.dp,
        (spotlightTarget.anchorTop + spotlightTarget.anchorHeight + 5.dp).toInt(),
        screenWidth - spotlightTarget.anchorLeft.toInt(),
        10.dp
      )
    } else {
      arrowParams.setMargins(
        spotlightTarget.anchorLeft.toInt(),
        (spotlightTarget.anchorTop + spotlightTarget.anchorHeight + 5.dp).toInt(),
        10.dp,
        10.dp
      )
    }
    (overlayBinding as TopLeftOverlayBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as TopLeftOverlayBinding).root
  }

  private val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
}
