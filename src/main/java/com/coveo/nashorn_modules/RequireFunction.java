package com.coveo.nashorn_modules;

import javax.script.Bindings;
import javax.script.ScriptException;

import jdk.nashorn.api.scripting.NashornException;

@FunctionalInterface
public interface RequireFunction {
  public Bindings require(String module) throws ScriptException, NashornException;
}
