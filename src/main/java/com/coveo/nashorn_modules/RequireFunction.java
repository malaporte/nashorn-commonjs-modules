package com.coveo.nashorn_modules;

import javax.script.ScriptException;

import jdk.nashorn.api.scripting.NashornException;

@FunctionalInterface
public interface RequireFunction {
  public Object require(String module) throws ScriptException, NashornException;
}
