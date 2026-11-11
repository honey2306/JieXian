package com.jiexian.app.enforce;

interface IShellService {
    /** Runs one shell command as the ADB shell user and returns its output. */
    String run(String command);
}
