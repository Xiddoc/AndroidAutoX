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
 * <h2>Host validation (WS7)</h2>
 * <p>{@link #createHostValidator()} builds a real {@link HostValidator} from the
 * {@link HostAllowlist} default, which restricts connections to the official Android
 * Auto gearhead host ({@code com.google.android.projection.gearhead}) signed with
 * the expected Google release certificate fingerprint.
 *
 * <p>The pure allowlist data and matching logic live in {@link HostAllowlist}
 * (100% unit-tested).  The only Android-coupled code here is the
 * {@link HostValidator.Builder} call — this glue class is excluded from the coverage gate.
 *
 * <p><b>Human-verification note:</b> the SHA-256 certificate digest baked into
 * {@link HostAllowlist#GEARHEAD_SHA256} should be verified against the device-installed
 * Android Auto build; see the Javadoc on that constant for the {@code adb} command.
 *
 * <p>This class is a framework-entry point and is excluded from the JaCoCo coverage gate.
 */
public final class AutoXCarAppService extends CarAppService {

    /**
     * Builds and returns a {@link HostValidator} that restricts Car App connections to
     * the official Android Auto gearhead host package and its known certificate digest(s).
     *
     * <p>The allowlist data is provided by {@link HostAllowlist#createDefault()}.
     * Each {@link HostAllowlist.HostEntry} is iterated; for each entry every digest is
     * registered with {@link HostValidator.Builder#addAllowedHost(String, String)} so
     * the builder accumulates the same set as the pure allowlist object.
     */
    @NonNull
    @Override
    public HostValidator createHostValidator() {
        HostAllowlist allowlist = HostAllowlist.createDefault();
        HostValidator.Builder builder = new HostValidator.Builder(getApplicationContext());
        for (HostAllowlist.HostEntry entry : allowlist.entries()) {
            for (String digest : entry.sha256Digests) {
                // addAllowedHost(packageName, sha256Digest): registers one package+digest
                // pair.  Called once per digest to support key-rotation entries that list
                // multiple fingerprints for the same host package.
                builder.addAllowedHost(entry.packageName, digest);
            }
        }
        return builder.build();
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
