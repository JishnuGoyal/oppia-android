package org.oppia.android.app.spotlight

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import com.takusemba.spotlight.Spotlight
import javax.inject.Inject
import org.oppia.android.app.model.ProfileId
import org.oppia.android.app.model.SpotlightViewState
import org.oppia.android.app.onboarding.SpotlightNavigationListener
import org.oppia.android.domain.spotlight.SpotlightStateController
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProviders.Companion.toLiveData

class SpotlightFragment @Inject constructor(
  val activity: AppCompatActivity,
  val fragment: Fragment,   // can this be removed?
  private val spotlightStateController: SpotlightStateController
) : Fragment(), SpotlightNavigationListener {
  val overlayPositionAutomator =  OverlayPositionAutomator(activity, fragment)
  private var spotlightTargetList = ArrayList<SpotlightTarget>()
  private lateinit var spotlight: Spotlight

  fun initialiseTargetList(spotlightTargets: ArrayList<SpotlightTarget>){
    spotlightTargetList = spotlightTargets
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    Log.d("overlay" , "inside on view created")

//    viewLifecycleOwner.lifecycleScope.launchWhenCreated {
//        for (i in spotlightTargetList) {
//          lifecycleScope.launchWhenCreated {
//            checkSpotlightViewState(spotlightTargetList[0])

//          }
//        }

//    }



  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View? {
    overlayPositionAutomator.createTarget(spotlightTargetList[0])
    overlayPositionAutomator.startSpotlight()
    return super.onCreateView(inflater, container, savedInstanceState)
  }

//  override fun onCreateView(
//    inflater: LayoutInflater,
//    container: ViewGroup?,
//    savedInstanceState: Bundle?
//  ): View? {
////    val binding: OverlayOverLeftBinding = OverlayOverLeftBinding.inflate(inflater)
////    return binding.root
//
//    checkSpotlightViewState(spotlightTargetList[0])
//
//  }



  private fun checkSpotlightViewState(spotlightTarget: SpotlightTarget) {

    val profileId = ProfileId.newBuilder()
      .setInternalId(123)
      .build()

    val featureViewStateLiveData =
      spotlightStateController.retrieveSpotlightViewState(
        profileId,
        spotlightTarget.feature
      ).toLiveData()

    featureViewStateLiveData.observe(
      viewLifecycleOwner,
      object : Observer<AsyncResult<SpotlightViewState>> {
        override fun onChanged(it: AsyncResult<SpotlightViewState>?) {
          if (it is AsyncResult.Success) {

            val viewState = (it.value)
            if (viewState == SpotlightViewState.SPOTLIGHT_SEEN) {
              return
            } else if (viewState == SpotlightViewState.SPOTLIGHT_VIEW_STATE_UNSPECIFIED || viewState == SpotlightViewState.SPOTLIGHT_NOT_SEEN) {

              Log.d("overlay",viewState.toString())
              Log.d("overlay", "adding target ")
              overlayPositionAutomator.createTarget(spotlightTarget)
              overlayPositionAutomator.startSpotlight()

              featureViewStateLiveData.removeObserver(this)
            }
          }
        }
      }

    )
  }




  override fun clickOnDismiss() {
    spotlight.finish()
  }

  override fun clickOnNextTip() {
    spotlight.next()
  }
}
