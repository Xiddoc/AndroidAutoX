package com.xiddoc.androidautox.autox;

import androidx.annotation.NonNull;
import androidx.car.app.Screen;
import androidx.car.app.Session;

/**
 * Jetpack Car App {@link Session} for the AutoX virtual-display projection.
 *
 * <p>A {@link Session} is created per Car App connection (one per Android Auto host
 * connection). This implementation is intentionally thin: its only job is to
 * instantiate the root {@link AutoXScreen} that drives the virtual display lifecycle.
 *
 * <p>The {@link AutoXScreen} is the owner of:
 * <ul>
 *   <li>The {@link VirtualDisplayController} (create on surface, release on surface gone).</li>
 *   <li>The {@link AppLauncher} that starts a guest app on the virtual display.</li>
 *   <li>The {@link GestureInjector} that routes touch events from the car digitizer.</li>
 * </ul>
 *
 * <p>This class is a framework-entry point and is excluded from the JaCoCo coverage gate.
 */
public final class AutoXSession extends Session {

    /**
     * Creates the initial {@link AutoXScreen} shown when the Car App connects.
     *
     * @param carContext the {@link androidx.car.app.CarContext} injected by the framework
     * @return a new {@link AutoXScreen}
     */
    @NonNull
    @Override
    public Screen onCreateScreen(@NonNull android.content.Intent intent) {
        return new AutoXScreen(getCarContext());
    }
}
