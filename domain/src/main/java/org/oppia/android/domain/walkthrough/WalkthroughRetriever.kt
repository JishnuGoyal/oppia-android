package org.oppia.android.domain.walkthrough

import javax.inject.Inject
import org.json.JSONObject
import org.oppia.android.app.model.State
import org.oppia.android.app.model.Walkthrough
import org.oppia.android.domain.util.JsonAssetRetriever
import org.oppia.android.domain.util.StateRetriever
import org.oppia.android.domain.util.getStringFromObject
import org.oppia.android.util.caching.AssetRepository
import org.oppia.android.util.caching.LoadLessonProtosFromAssets

class WalkthroughRetriever @Inject constructor(
  private val jsonAssetRetriever: JsonAssetRetriever,
  private val stateRetriever: StateRetriever,
  private val assetRepository: AssetRepository,
  @LoadLessonProtosFromAssets private val loadLessonProtosFromAssets: Boolean
) {
  // TODO(#169): Force callers of this method on a background thread.
  /** Loads and returns an walkthrough for the specified walkthrough ID, or fails. */
  fun loadWalkthrough(walkthroughId: String): Walkthrough {
    return if (loadLessonProtosFromAssets) {
      assetRepository.loadProtoFromLocalAssets(walkthroughId, Walkthrough.getDefaultInstance())
    } else {
      val walkthroughObject =
        jsonAssetRetriever.loadJsonFromAsset("$walkthroughId.json")
          ?: return Walkthrough.getDefaultInstance()
      loadWalkthroughFromAsset(walkthroughObject)
    }
  }

  // Returns an walkthrough given an assetName
  private fun loadWalkthroughFromAsset(walkthroughObject: JSONObject): Walkthrough {
    val innerWalkthroughObject = walkthroughObject.getJSONObject("walkthrough")
    return Walkthrough.newBuilder()
      .setWalkthroughId(walkthroughObject.getStringFromObject("walkthrough_id"))
      .setTitle(innerWalkthroughObject.getStringFromObject("title"))
      .setLanguageCode(innerWalkthroughObject.getStringFromObject("language_code"))
      .setVersion(walkthroughObject.getInt("version"))
      .build()
  }

  // Creates the states map from JSON
  private fun createStatesFromJsonObject(statesJsonObject: JSONObject?): MutableMap<String, State> {
    val statesMap: MutableMap<String, State> = mutableMapOf()
    val statesKeys = statesJsonObject?.keys() ?: return statesMap
    val statesIterator = statesKeys.iterator()
    while (statesIterator.hasNext()) {
      val key = statesIterator.next()
      statesMap[key] = stateRetriever.createStateFromJson(key, statesJsonObject.getJSONObject(key))
    }
    return statesMap
  }
}