package org.oppia.android.app.home

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import org.oppia.android.R
import org.oppia.android.app.activity.ActivityScope
import org.oppia.android.app.drawer.NavigationDrawerFragment
import javax.inject.Inject
import org.oppia.android.app.drawer.NAVIGATION_PROFILE_ID_ARGUMENT_KEY
import org.oppia.android.app.spotlight.SpotlightFragment
import org.oppia.android.app.spotlight.SpotlightTarget
import org.oppia.android.app.spotlight.SpotlightTargetReference

const val TAG_HOME_FRAGMENT = "HOME_FRAGMENT"
private const val TAG_HOME_SPOTLIGHT_FRAGMENT = "HomeActivity.SpotlightFragment.tag"

/** The presenter for [HomeActivity]. */
@ActivityScope
class HomeActivityPresenter @Inject constructor(private val activity: AppCompatActivity) {
  private var navigationDrawerFragment: NavigationDrawerFragment? = null

  fun handleOnCreate() {
    activity.setContentView(R.layout.home_activity)
    setUpNavigationDrawer()
    if (getHomeFragment() == null) {
      activity.supportFragmentManager.beginTransaction().add(
        R.id.home_fragment_placeholder,
        HomeFragment(),
        TAG_HOME_FRAGMENT
      ).commitNow()
    }
    if (getSpotlightFragment() == null) {
      // TODO: Profile ID retrieval should be done more cleanly.
      val internalProfileId = activity.intent.getIntExtra(NAVIGATION_PROFILE_ID_ARGUMENT_KEY, -1)
      val spotlightFragment = SpotlightFragment()
      // TODO: Pass references in via fragment args to make sure they're present when needed. The
      //  current solution will crash for configuration changes since the initialized list will be
      //  lost.
      val titleTarget =
        SpotlightTargetReference(
          anchorViewId = R.id.section_title_text_view,
          hint = "Test hint",
          feature = org.oppia.android.app.model.Spotlight.FeatureCase.PROMOTED_STORIES
        )
      spotlightFragment.initialiseTargetList(listOf(titleTarget), internalProfileId)

      // Spotlight fragment must be committed after target initialization.
      activity.supportFragmentManager.beginTransaction().add(
        R.id.home_spotlight_fragment_container_placeholder,
        spotlightFragment,
        TAG_HOME_SPOTLIGHT_FRAGMENT
      ).commitNow()
    }
  }

  fun handleOnRestart() {
    setUpNavigationDrawer()
  }

  private fun setUpNavigationDrawer() {
    val toolbar = activity.findViewById<View>(R.id.home_activity_toolbar) as Toolbar
    activity.setSupportActionBar(toolbar)
    activity.supportActionBar!!.setDisplayShowHomeEnabled(true)
    navigationDrawerFragment = activity
      .supportFragmentManager
      .findFragmentById(R.id.home_activity_fragment_navigation_drawer) as NavigationDrawerFragment
    navigationDrawerFragment!!.setUpDrawer(
      activity.findViewById<View>(R.id.home_activity_drawer_layout) as DrawerLayout,
      toolbar, R.id.nav_home
    )
  }

  private fun getHomeFragment(): HomeFragment? {
    return activity.supportFragmentManager.findFragmentById(
      R.id.home_fragment_placeholder
    ) as? HomeFragment
  }

  private fun getSpotlightFragment(): SpotlightFragment? {
    return activity.supportFragmentManager.findFragmentById(
      R.id.home_spotlight_fragment_container_placeholder
    ) as? SpotlightFragment
  }
}
