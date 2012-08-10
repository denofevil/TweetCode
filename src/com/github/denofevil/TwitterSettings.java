package com.github.denofevil;

import com.intellij.openapi.components.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dennis.Ushakov
 */
@State(
    name = "TweetCodeTwitterSettings",
    storages = {
        @Storage(
            file = StoragePathMacros.APP_CONFIG + "/tweet_code_settings.xml"
        )
    }
)
public class TwitterSettings implements PersistentStateComponent<TwitterSettings> {
  public String myToken;
  public String mySecret;

  @Nullable
  @Override
  public TwitterSettings getState() {
    return this;
  }

  @Override
  public void loadState(TwitterSettings twitterSettings) {
    myToken = twitterSettings.myToken;
    mySecret =twitterSettings.mySecret;
  }

  public static TwitterSettings getInstance() {
    return ServiceManager.getService(TwitterSettings.class);
  }
}
