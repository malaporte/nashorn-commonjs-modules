package com.coveo.nashorn_modules;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;

import jdk.nashorn.api.scripting.NashornScriptEngine;

public class Require {
  public static Module enable(NashornScriptEngine engine, Folder folder) throws ScriptException {
    Bindings global = engine.getBindings(ScriptContext.ENGINE_SCOPE);
    Module module = new Module(engine, folder, new ModuleCache(), "<main>", global, null, null);
    module.setLoaded();
    return module;
  }
}
