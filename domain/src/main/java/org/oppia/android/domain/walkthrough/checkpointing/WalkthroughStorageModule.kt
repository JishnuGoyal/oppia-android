//package org.oppia.android.domain.walkthrough.checkpointing
//
//import dagger.Module
//import dagger.Provides
//import javax.inject.Qualifier
//
//@Qualifier
//annotation class WalkthroughStorageDatabaseSize
//
///** Provider to return any constants required during the storage of walkthrough checkpoints. */
//@Module
//class WalkthroughStorageModule {
//
//  /**
//   * Provides the size allocated to walkthrough checkpoint database.
//   *
//   * The current [WalkthroughStorageDatabaseSize] is set to 2097152 Bytes that is equal to 2MB
//   * per profile.
//   *
//   * Taking 20 KB per checkpoint, it is expected to store about 100 checkpoints for every profile
//   * before the database exceeds the allocated limit.
//   */
//  @Provides
//  @WalkthroughStorageDatabaseSize
//  fun provideWalkthroughStorageDatabaseSize(): Int = 2097152
//}