package com.wexresprise.margytube.plugin;

import java.util.ArrayList;
import java.util.List;

public class PluginInfo {
    public String id = "";
    public String name = "";
    public String version = "";
    public String author = "";
    public String description = "";
    public String entry = "";
    public String dirPath = "";
    public List<String> hooks = new ArrayList<>();
    public boolean dex;
    public boolean enabled = true;
    public String error;
}
