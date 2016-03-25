package com.coveo.nashorn_modules;

public class Paths {
  public static String[] splitPath(String path) {
    return path.split("[\\\\/]");
  }
}
