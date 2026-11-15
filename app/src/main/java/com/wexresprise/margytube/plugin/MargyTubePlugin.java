package com.wexresprise.margytube.plugin;

/**
 * Optional native part of a plugin. Compiled against this interface, shipped as
 * classes.dex inside the .mgtp archive. Return null to leave the feed untouched.
 */
public interface MargyTubePlugin {
    String onFeed(String feedJson);

    String onVideo(String videoId);
}
