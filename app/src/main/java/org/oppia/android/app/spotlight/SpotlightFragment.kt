package org.oppia.android.app.spotlight

import android.content.Context
import android.content.res.Resources
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.takusemba.spotlight.OnSpotlightListener
import com.takusemba.spotlight.OnTargetListener
import com.takusemba.spotlight.Spotlight
import com.takusemba.spotlight.Target
import com.takusemba.spotlight.shape.Circle
import com.takusemba.spotlight.shape.RoundedRectangle
import com.takusemba.spotlight.shape.Shape
import java.util.*
import javax.inject.Inject
import org.oppia.android.R
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.SpotlightViewState
import org.oppia.android.app.onboarding.SpotlightNavigationListener
import org.oppia.android.databinding.OverlayOverLeftBinding
import org.oppia.android.databinding.OverlayOverRightBinding
import org.oppia.android.databinding.OverlayUnderLeftBinding
import org.oppia.android.databinding.OverlayUnderRightBinding
import org.oppia.android.domain.spotlight.SpotlightStateController
import org.oppia.android.util.accessibility.AccessibilityServiceImpl
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProviders.Companion.toLiveData

class SpotlightFragment @Inject constructor(
  private val activity: AppCompatActivity,
  private val spotlightStateController: SpotlightStateController,
  private val accessibilityServiceImpl: AccessibilityServiceImpl
) : Fragment(), SpotlightNavigationListener {
  private var targetList = ArrayList<Target>()
  private var spotlightTargetList = ArrayList<SpotlightTarget>()
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

  fun initialiseTargetList(spotlightTargets: ArrayList<SpotlightTarget>, profileId: Int) {
    spotlightTargetList = spotlightTargets
    internalProfileId = profileId
  }

  // since this fragment does not have any view to inflate yet, all the tasks should be done here.
  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (accessibilityServiceImpl.isScreenReaderEnabled()) {
      activity.supportFragmentManager.beginTransaction().remove(this)
    }else {
      calculateScreenSize()
      checkIsRTL()
      spotlightTargetList.forEachIndexed { _, spotlightTarget ->
        checkSpotlightViewState(spotlightTarget)
      }
    }
  }

  private fun checkSpotlightViewState(spotlightTarget: SpotlightTarget) {
    var counter = 0
    val profileId = ProfileId.newBuilder()
      .setInternalId(internalProfileId)
      .build()

    val featureViewStateLiveData =
      spotlightStateController.retrieveSpotlightViewState(
        profileId,
        spotlightTarget.feature
      ).toLiveData()

    // use activity as observer because this fragment's view hasn't been created yet.
    featureViewStateLiveData.observe(
      activity,
      object : Observer<AsyncResult<SpotlightViewState>> {
        override fun onChanged(it: AsyncResult<SpotlightViewState>?) {
          if (it is AsyncResult.Success) {
            val viewState = (it.value)
            if (viewState == SpotlightViewState.SPOTLIGHT_SEEN) {
              return
            } else if (viewState == SpotlightViewState.SPOTLIGHT_NOT_SEEN) {
              createTarget(spotlightTarget)
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

  private fun createTarget(
    spotlightTarget: SpotlightTarget
  ) {
    val target = Target.Builder()
      .setAnchor(spotlightTarget.anchor)
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
          spotlightTarget.anchorHeight.toFloat(),
          spotlightTarget.anchorWidth.toFloat(),
          24f
        )
      }
      SpotlightShape.Circle -> {
        return if (spotlightTarget.anchorHeight > spotlightTarget.anchorWidth) {
          Circle((spotlightTarget.anchorHeight / 2).toFloat())
        } else {
          Circle((spotlightTarget.anchorWidth / 2).toFloat())
        }
      }
    }
  }

  private fun checkIsRTL() {
    val locale = Locale.getDefault()
    val directionality: Byte = Character.getDirectionality(locale.displayName[0].toInt())
    isRTL = directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
  }

  private fun getArrowWidth(): Float {
    return this.resources.getDimension(R.dimen.arrow_width)
  }

  private fun getScreenCentreY(): Int {
    return screenHeight / 2
  }

  private fun getScreenCentreX(): Int {
    return screenWidth / 2
  }

  private fun calculateAnchorPosition(spotlightTarget: SpotlightTarget) {
    anchorPosition = if (spotlightTarget.anchorCentreX > getScreenCentreX()) {
      if (spotlightTarget.anchorCentreY > getScreenCentreY()) {
        AnchorPosition.BottomRight
      } else {
        AnchorPosition.TopRight
      }
    } else if (spotlightTarget.anchorCentreY > getScreenCentreY()) {
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
    overlayBinding = OverlayOverLeftBinding.inflate(this.layoutInflater)
    (overlayBinding as OverlayOverLeftBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as OverlayOverLeftBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as OverlayOverLeftBinding).arrow.layoutParams
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
    (overlayBinding as OverlayOverLeftBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as OverlayOverLeftBinding).root
  }

  private fun configureBottomRightOverlay(spotlightTarget: SpotlightTarget): View {
    overlayBinding = OverlayOverRightBinding.inflate(this.layoutInflater)
    (overlayBinding as OverlayOverRightBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as OverlayOverRightBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as OverlayOverRightBinding).arrow.layoutParams
      as ViewGroup.MarginLayoutParams
    if (isRTL) {
      arrowParams.setMargins(
        10.dp,
        (spotlightTarget.anchorTop.toInt() - spotlightTarget.anchorHeight - 5.dp).toInt(),
        screenWidth - (spotlightTarget.anchorLeft + spotlightTarget.anchorWidth - getArrowWidth()).toInt(),
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
    (overlayBinding as OverlayOverRightBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as OverlayOverRightBinding).root
  }

  private fun configureTopRightOverlay(spotlightTarget: SpotlightTarget): View {
    overlayBinding = OverlayUnderRightBinding.inflate(layoutInflater)
    (overlayBinding as OverlayUnderRightBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as OverlayUnderRightBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as OverlayUnderRightBinding).arrow.layoutParams
      as ViewGroup.MarginLayoutParams
    if (isRTL) {
      arrowParams.setMargins(
        10.dp,
        (spotlightTarget.anchorTop + spotlightTarget.anchorHeight + 5.dp).toInt(),
        screenWidth - (spotlightTarget.anchorLeft + spotlightTarget.anchorWidth - getArrowWidth()).toInt(),
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
    (overlayBinding as OverlayUnderRightBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as OverlayUnderRightBinding).root
  }

  private fun configureTopLeftOverlay(spotlightTarget: SpotlightTarget): View {
    overlayBinding = OverlayUnderLeftBinding.inflate(this.layoutInflater)
    (overlayBinding as OverlayUnderLeftBinding).let {
      it.lifecycleOwner = this
      it.presenter = this
    }

    (overlayBinding as OverlayUnderLeftBinding).customText.text = spotlightTarget.hint

    val arrowParams = (overlayBinding as OverlayUnderLeftBinding).arrow.layoutParams
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
    (overlayBinding as OverlayUnderLeftBinding).arrow.layoutParams = arrowParams

    return (overlayBinding as OverlayUnderLeftBinding).root
  }

  private val Int.dp: Int
    get() = (this * Resources.getSystem().displayMetrics.density + 0.5f).toInt()
}
