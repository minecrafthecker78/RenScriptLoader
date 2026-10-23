package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import org.lwjgl.glfw.GLFW;

public class Bypassingzz extends Module {
    public Bypassingzz() {
        super("Bypass", ModuleCategory.Dev, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        Client.notificationManager.addNotification("Disabler", "Doing exploit, dont move");
    }

    @Override
    public void onDisable() {

    }

}
