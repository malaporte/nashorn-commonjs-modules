package com.coveo.nashorn_modules;

public interface Folder {
  public Folder getParent();

  public String getPath();

  public String getFile(String name);

  public Folder getFolder(String name);
}
