package com.xiddoc.androidautox.autox;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/**
 * Jetpack Car App {@link CarAppService} entry point for the AutoX virtual-display
 * projection feature.
 *
 * <p>This service is declared in the manifest with the
 * {@code androidx.car.app.CarAppService} action and the
 * {@code androidx.car.app.category.NAVIGATION} category so the Android Auto host
 * discovers it as a navigation-category car app.
 *
 * <h2>Session lifecycle</h2>
 * <p>{@link #onCreateSession()} returns a new {@link AutoXSession} for each Car App
 * connection. The session owns the {@link AutoXScreen} which drives the virtual-display
 * lifecycle (create on surface available, release on surface destroyed).
 *
 * <h2>Host validation</h2>
 * <p>{@link #createHostValidator()} returns {@link HostValidator#ALLOW_ALL_HOSTS_VALIDATOR}
 * for development convenience.
 *
 * <p><b>Production caveat:</b> {@code ALLOW_ALL_HOSTS_VALIDATOR} accepts connections
 * from any host including unofficial ones. For a production release, replace this with
 * a {@link HostValidator} built from the official Android Auto host certificate
 * fingerprints to prevent rogue hosts from connecting. See the Jetpack Car App
 * {@code HostValidator.Builder} documentation for details.
 *
 * <p>This class is a framework-entry point and is excluded from the JaCoCo coverage gate.
 */
public final class AutoXCarAppService extends CarAppService {

    /**
     * Returns {@link HostValidator#ALLOW_ALL_HOSTS_VALIDATOR}.
     *
     * <p><b>IMPORTANT — production caveat:</b> replace with a certificate-fingerprint
     * based validator before publishing to the Play Store. The allow-all validator is
     * acceptable only for development / sideloaded builds.
     */
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    /**
     * Creates and returns a new {@link AutoXSession} that drives the virtual-display
     * projection for this Car App connection.
     */
    @NonNull
    @Override
    public Session onCreateSession() {
        return new AutoXSession();
    }
}
