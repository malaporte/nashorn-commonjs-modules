package com.coveo.nashorn_modules;

import jdk.nashorn.api.scripting.NashornScriptEngine;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptException;

public class Require {
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
}
