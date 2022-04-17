package org.oppia.android.domain.walkthrough.checkpointing

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Deferred
import org.oppia.android.app.model.CheckpointState
import org.oppia.android.app.model.WalkthroughCheckpoint
import org.oppia.android.app.model.WalkthroughCheckpointDatabase
import org.oppia.android.app.model.WalkthroughCheckpointDetails
import org.oppia.android.app.model.ProfileId
import org.oppia.android.data.persistence.PersistentCacheStore
import org.oppia.android.domain.walkthrough.WalkthroughRetriever
import org.oppia.android.domain.oppialogger.OppiaLogger
import org.oppia.android.util.data.AsyncResult
import org.oppia.android.util.data.DataProvider
import org.oppia.android.util.data.DataProviders
import org.oppia.android.util.data.DataProviders.Companion.transformAsync
import javax.inject.Inject
import javax.inject.Singleton

private const val CACHE_NAME = "walkthrough_checkpoint_database"
private const val RETRIEVE_EXPLORATION_CHECKPOINT_DATA_PROVIDER_ID =
  "retrieve_walkthrough_checkpoint_provider_id"
private const val RETRIEVE_OLDEST_CHECKPOINT_DETAILS_DATA_PROVIDER_ID =
  "retrieve_oldest_checkpoint_details_provider_id"
private const val RECORD_EXPLORATION_CHECKPOINT_DATA_PROVIDER_ID =
  "record_walkthrough_checkpoint_provider_id"
private const val DELETE_EXPLORATION_CHECKPOINT_DATA_PROVIDER_ID =
  "delete_walkthrough_checkpoint_provider_id"
private const val CHECK_IS_EXPLORATION_CHECKPOINT_COMPATIBLE_WITH_EXPLORATION_DATA_PROVIDER_ID =
  "check_is_walkthrough_checkpoint_compatible_with_walkthrough_provider_id"

/**
 * Controller for saving, retrieving, updating, and deleting walkthrough checkpoints.
 */
@Singleton
class WalkthroughCheckpointController @Inject constructor(
  private val cacheStoreFactory: PersistentCacheStore.Factory,
  private val dataProviders: DataProviders,
  private val oppiaLogger: OppiaLogger,
  @WalkthroughStorageDatabaseSize private val walkthroughCheckpointDatabaseSizeLimit: Int,
  private val walkthroughRetriever: WalkthroughRetriever
) {

  /** Indicates that no checkpoint was found for the specified walkthroughId and profileId. */
  class WalkthroughCheckpointNotFoundException(message: String) : Exception(message)

  /** Indicates that no checkpoint was found for the specified walkthroughId and profileId. */
  class OutdatedWalkthroughCheckpointException(message: String) : Exception(message)

  /**
   * These Statuses correspond to the result of the deferred such that if the deferred contains
   *
   * CHECKPOINT_NOT_FOUND, the [WalkthroughCheckpointNotFoundException] will be passed to a failed
   * AsyncResult.
   *
   * SUCCESS corresponds to a successful AsyncResult.
   */
  enum class WalkthroughCheckpointActionStatus {
    CHECKPOINT_NOT_FOUND,
    SUCCESS
  }

  private val cacheStoreMap =
    mutableMapOf<ProfileId, PersistentCacheStore<WalkthroughCheckpointDatabase>>()

  /**
   * Records an walkthrough checkpoint for the specified profile.
   *
   * @return a [Deferred] that upon completion indicates the current [CheckpointState].
   *     If the size of the checkpoint database is less than the allocated limit of
   *     [WalkthroughStorageDatabaseSize] then the deferred upon completion gives the result
   *     [CheckpointState.CHECKPOINT_SAVED_DATABASE_NOT_EXCEEDED_LIMIT]. If the size of the
   *     checkpoint database exceeded [WalkthroughStorageDatabaseSize] then
   *     [CheckpointState.CHECKPOINT_SAVED_DATABASE_EXCEEDED_LIMIT] is returned upon successful
   *     completion of deferred.
   */
  internal fun recordWalkthroughCheckpointAsync(
    profileId: ProfileId,
    walkthroughId: String,
    walkthroughCheckpoint: WalkthroughCheckpoint
  ): Deferred<CheckpointState> {
    return retrieveCacheStore(profileId).storeDataWithCustomChannelAsync(
      updateInMemoryCache = true
    ) {
      val walkthroughCheckpointDatabaseBuilder = it.toBuilder()

      val checkpoint = walkthroughCheckpointDatabaseBuilder
        .walkthroughCheckpointMap[walkthroughId]

      // Add checkpoint to the map if it was not saved previously.
      if (checkpoint == null) {
        walkthroughCheckpointDatabaseBuilder
          .putWalkthroughCheckpoint(walkthroughId, walkthroughCheckpoint)
      } else {
        // Update the timestamp to the time when the checkpoint was saved for the first time and
        // then replace the existing checkpoint in the map with the updated checkpoint.
        walkthroughCheckpointDatabaseBuilder.putWalkthroughCheckpoint(
          walkthroughId,
          walkthroughCheckpoint.toBuilder()
            .setTimestampOfFirstCheckpoint(checkpoint.timestampOfFirstCheckpoint)
            .build()
        )
      }

      val walkthroughCheckpointDatabase = walkthroughCheckpointDatabaseBuilder.build()

      if (walkthroughCheckpointDatabase.serializedSize <= walkthroughCheckpointDatabaseSizeLimit) {
        Pair(
          walkthroughCheckpointDatabase,
          CheckpointState.CHECKPOINT_SAVED_DATABASE_NOT_EXCEEDED_LIMIT
        )
      } else {
        Pair(
          walkthroughCheckpointDatabase,
          CheckpointState.CHECKPOINT_SAVED_DATABASE_EXCEEDED_LIMIT
        )
      }
    }
  }

  /**
   * Returns a [DataProvider] for the [Deferred] returned from [recordWalkthroughCheckpointAsync].
   */
  fun recordWalkthroughCheckpoint(
    profileId: ProfileId,
    walkthroughId: String,
    walkthroughCheckpoint: WalkthroughCheckpoint
  ): DataProvider<Any?> {
    val deferred = recordWalkthroughCheckpointAsync(
      profileId,
      walkthroughId,
      walkthroughCheckpoint
    )
    return dataProviders.createInMemoryDataProviderAsync(
      RECORD_EXPLORATION_CHECKPOINT_DATA_PROVIDER_ID
    ) {
      return@createInMemoryDataProviderAsync AsyncResult.Success(deferred.await())
    }
  }

  /** Returns the saved checkpoint for a specified walkthroughId and profileId. */
  fun retrieveWalkthroughCheckpoint(
    profileId: ProfileId,
    walkthroughId: String
  ): DataProvider<WalkthroughCheckpoint> {
    return retrieveCacheStore(profileId)
      .transformAsync(
        RETRIEVE_EXPLORATION_CHECKPOINT_DATA_PROVIDER_ID
      ) { walkthroughCheckpointDatabase ->

        val checkpoint = walkthroughCheckpointDatabase.walkthroughCheckpointMap[walkthroughId]
        val walkthrough = walkthroughRetriever.loadWalkthrough(walkthroughId)

        when {
          checkpoint != null && walkthrough.version == checkpoint.walkthroughVersion -> {
            AsyncResult.Success(checkpoint)
          }
          checkpoint != null && walkthrough.version != checkpoint.walkthroughVersion -> {
            AsyncResult.Failure(
              OutdatedWalkthroughCheckpointException(
                "checkpoint with version: ${checkpoint.walkthroughVersion} cannot be used to " +
                  "resume walkthrough $walkthroughId with version: ${walkthrough.version}"
              )
            )
          }
          else -> {
            AsyncResult.Failure(
              WalkthroughCheckpointNotFoundException(
                "Checkpoint with the walkthroughId $walkthroughId was not found " +
                  "for profileId ${profileId.internalId}."
              )
            )
          }
        }
      }
  }

  /**
   * Retrieves details about the oldest saved walkthrough checkpoint.
   *
   * @return [WalkthroughCheckpointDetails]  which contains the walkthroughId, walkthroughTitle
   *      and walkthroughVersion of the oldest saved checkpoint for the specified profile.
   */
  fun retrieveOldestSavedWalkthroughCheckpointDetails(
    profileId: ProfileId
  ): DataProvider<WalkthroughCheckpointDetails> {
    return retrieveCacheStore(profileId)
      .transformAsync(
        RETRIEVE_OLDEST_CHECKPOINT_DETAILS_DATA_PROVIDER_ID
      ) { walkthroughCheckpointDatabase ->

        // Find the oldest checkpoint by timestamp or null if no checkpoints is saved.
        val oldestCheckpoint =
          walkthroughCheckpointDatabase.walkthroughCheckpointMap.minByOrNull {
            it.value.timestampOfFirstCheckpoint
          }

        if (oldestCheckpoint != null) {
          val walkthroughCheckpointDetails = WalkthroughCheckpointDetails.newBuilder()
            .setWalkthroughId(oldestCheckpoint.key)
            .setWalkthroughTitle(oldestCheckpoint.value.walkthroughTitle)
            .setWalkthroughVersion(oldestCheckpoint.value.walkthroughVersion)
            .build()
          AsyncResult.Success(walkthroughCheckpointDetails)
        } else {
          AsyncResult.Failure(
            WalkthroughCheckpointNotFoundException(
              "No saved checkpoints in $CACHE_NAME for profileId ${profileId.internalId}."
            )
          )
        }
      }
  }

  /** Deletes the saved checkpoint for a specified walkthroughId and profileId. */
  fun deleteSavedWalkthroughCheckpoint(
    profileId: ProfileId,
    walkthroughId: String
  ): DataProvider<Any?> {
    val deferred = retrieveCacheStore(profileId).storeDataWithCustomChannelAsync(
      updateInMemoryCache = true
    ) { walkthroughCheckpointDatabase ->

      if (!walkthroughCheckpointDatabase.walkthroughCheckpointMap.containsKey(walkthroughId)) {
        return@storeDataWithCustomChannelAsync Pair(
          walkthroughCheckpointDatabase,
          WalkthroughCheckpointActionStatus.CHECKPOINT_NOT_FOUND
        )
      }

      val walkthroughCheckpointDatabaseBuilder = walkthroughCheckpointDatabase.toBuilder()

      walkthroughCheckpointDatabaseBuilder
        .removeWalkthroughCheckpoint(walkthroughId)

      Pair(
        walkthroughCheckpointDatabaseBuilder.build(),
        WalkthroughCheckpointActionStatus.SUCCESS
      )
    }
    return dataProviders.createInMemoryDataProviderAsync(
      DELETE_EXPLORATION_CHECKPOINT_DATA_PROVIDER_ID
    ) {
      return@createInMemoryDataProviderAsync getDeferredResult(
        deferred = deferred,
        profileId = profileId,
        walkthroughId = walkthroughId
      )
    }
  }

  private suspend fun getDeferredResult(
    deferred: Deferred<WalkthroughCheckpointActionStatus>,
    walkthroughId: String?,
    profileId: ProfileId?,
  ): AsyncResult<Any?> {
    return when (deferred.await()) {
      WalkthroughCheckpointActionStatus.CHECKPOINT_NOT_FOUND ->
        AsyncResult.Failure(
          WalkthroughCheckpointNotFoundException(
            "No saved checkpoint with walkthroughId ${walkthroughId!!} found for " +
              "the profileId ${profileId!!.internalId}."
          )
        )
      WalkthroughCheckpointActionStatus.SUCCESS -> AsyncResult.Success(null)
    }
  }

  private fun retrieveCacheStore(
    profileId: ProfileId
  ): PersistentCacheStore<WalkthroughCheckpointDatabase> {
    val cacheStore = if (profileId in cacheStoreMap) {
      cacheStoreMap[profileId]!!
    } else {
      val cacheStore =
        cacheStoreFactory.createPerProfile(
          CACHE_NAME,
          WalkthroughCheckpointDatabase.getDefaultInstance(),
          profileId
        )
      cacheStoreMap[profileId] = cacheStore
      cacheStore
    }

    cacheStore.primeCacheAsync().invokeOnCompletion { throwable ->
      throwable?.let {
        oppiaLogger.e(
          "WalkthroughCheckpointController",
          "Failed to prime cache ahead of data retrieval for WalkthroughCheckpointController.",
          it
        )
      }
    }
    return cacheStore
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  fun getWalkthroughCheckpointDatabaseSizeLimit(): Int = walkthroughCheckpointDatabaseSizeLimit
}
