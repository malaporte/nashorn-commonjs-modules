package com.coveo.nashorn_modules;

import javax.script.ScriptException;
import java.util.List;

public interface FileHandler {
  List<String> getFileEndings();

  Module compile(Folder parent, String fullPath, String code, Module parentModule)
      throws ScriptException;
}
