package com.coveo.nashorn_modules;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import jdk.nashorn.api.scripting.NashornScriptEngine;

import java.util.HashMap;
import java.util.Map;

public class Require {

  private static Map<String, FileHandler> handlers;

  public static Module enable(NashornScriptEngine engine, Folder folder) throws ScriptException {
    Bindings module = engine.createBindings();
    Bindings exports = engine.createBindings();
    Module created =
        new Module(engine, folder, new ModuleCache(), "<main>", module, exports, null, null);
    created.setLoaded();

    Bindings global = engine.getBindings(ScriptContext.ENGINE_SCOPE);
    global.put("require", created);
    global.put("module", module);
    global.put("exports", exports);

    return created;
  }

  public static void registerHandler(FileHandler fileHandler) {
    if (handlers == null) {
      handlers = new HashMap<>();
    }
    for (String fileEnding : fileHandler.getFileEndings()) {
      handlers.put(fileEnding, fileHandler);
    }
  }

  static FileHandler getFileHandler(String fileEnding) {
    return handlers != null ? handlers.get(fileEnding) : null;
  }
}
