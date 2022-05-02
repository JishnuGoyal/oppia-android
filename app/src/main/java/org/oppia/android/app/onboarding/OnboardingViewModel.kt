package org.oppia.android.app.onboarding

import androidx.databinding.ObservableField
import androidx.lifecycle.ViewModel
import javax.inject.Inject
import org.oppia.android.R
import org.oppia.android.app.model.OnboardingSpotlightCheckpoint
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.SpotlightState
import org.oppia.android.app.translation.AppLanguageResourceHandler
import org.oppia.android.app.viewmodel.ObservableViewModel
import org.oppia.android.domain.spotlight.SpotlightStateController

private const val INITIAL_SLIDE_NUMBER = 0

/** [ViewModel] for [OnboardingFragment]. */
class OnboardingViewModel @Inject constructor(
  private val resourceHandler: AppLanguageResourceHandler,
  private val spotlightStateController: SpotlightStateController
) : ObservableViewModel() {
  val slideNumber = ObservableField(INITIAL_SLIDE_NUMBER)
  val totalNumberOfSlides = TOTAL_NUMBER_OF_SLIDES
  val slideDotsContainerContentDescription =
    ObservableField(computeSlideDotsContainerContentDescription(INITIAL_SLIDE_NUMBER))

  fun slideChanged(slideIndex: Int) {
    slideNumber.set(slideIndex)
    slideDotsContainerContentDescription.set(
      computeSlideDotsContainerContentDescription(slideIndex)
    )
  }

  private fun computeSlideDotsContainerContentDescription(slideNumber: Int): String {
    return resourceHandler.getStringInLocaleWithWrapping(
      R.string.onboarding_slide_dots_content_description,
      (slideNumber + 1).toString(),
      totalNumberOfSlides.toString()
    )
  }

  fun recordSpotlightCheckpoint(
    lastScreenViewed: OnboardingSpotlightCheckpoint.LastScreenViewed,
    spotlightState: SpotlightState
  ) {
    val checkpoint = OnboardingSpotlightCheckpoint.newBuilder()
      .setLastScreenViewed(lastScreenViewed)
      .setSpotlightState(spotlightState)
      .build()

    val profileId = ProfileId.newBuilder()
      .setInternalId(123)
      .build()
    spotlightStateController.recordSpotlightCheckpoint(profileId, checkpoint)
  }

}
