package io.sonarswift.plugin.rules;

import io.sonarswift.plugin.rules.swift.ForceUnwrapCheck;
import io.sonarswift.plugin.rules.swift.PrintStatementCheck;
import io.sonarswift.plugin.rules.swift.TodoCommentCheck;
import io.sonarswift.plugin.rules.swift.ForceCastCheck;
import io.sonarswift.plugin.rules.swift.ForceTryCheck;
import io.sonarswift.plugin.rules.swift.HardcodedSecretCheck;
import io.sonarswift.plugin.rules.swift.WeakCryptoMD5Check;
import io.sonarswift.plugin.rules.swift.InsecureUrlCheck;
import io.sonarswift.plugin.rules.swift.concurrency.ActorIsolationViolationCheck;
import io.sonarswift.plugin.rules.swift.concurrency.DetachedTaskMisuseCheck;
import io.sonarswift.plugin.rules.swift.concurrency.MainActorViolationCheck;
import io.sonarswift.plugin.rules.swift.concurrency.MissingAwaitCheck;
import io.sonarswift.plugin.rules.swift.concurrency.NonisolatedUnsafeHotspotCheck;
import io.sonarswift.plugin.rules.swift.concurrency.SendableViolationCheck;
import io.sonarswift.plugin.rules.swift.concurrency.SharedMutableStateCheck;
import io.sonarswift.plugin.rules.swift.concurrency.TaskRetainCycleCheck;
import io.sonarswift.plugin.rules.swift.concurrency.UncheckedSendableHotspotCheck;

import java.util.List;

/**
 * Canonical list of every Swift rule the plugin ships.
 * Order doesn't matter; matching JSON + HTML files must exist under
 * {@code resources/io/sonarswift/plugin/rules/swift/SXXXX.{json,html}}.
 *
 * <p>The {@code add-rule} skill keeps this list, the resource files, and the
 * check classes in sync.</p>
 */
public final class SwiftCheckList {

    private SwiftCheckList() {}

    public static final List<String> KEYS = List.of(
            // ---- code smells ----
            "S1001", // force-unwrap of optional
            "S1003", // force-cast
            "S1004", // force-try
            "S1005", // print(...) left in code
            "S1007", // TODO / FIXME comment

            // ---- vulnerabilities ----
            "S1800", // hardcoded API key
            "S1802", // http:// URL
            "S1804", // weak crypto: MD5

            // ---- concurrency (Swift 6 strict) ----
            "S1900", // non-Sendable class crossing concurrency boundary
            "S1901", // @unchecked Sendable hotspot
            "S1902", // mutating actor state without await
            "S1903", // UI API touched from non-MainActor
            "S1904", // Task { } strong self capture
            "S1905", // Task.detached misuse
            "S1906", // missing await on async call
            "S1907", // static var outside actor
            "S1908"  // nonisolated(unsafe) hotspot
            // ... more keys added incrementally as rules ship
    );

    /**
     * Maps a rule key to its Java check class. Used by the SwiftSensor
     * to instantiate active checks at scan time.
     */
    public static final List<Class<?>> CHECK_CLASSES = List.of(
            ForceUnwrapCheck.class,
            ForceCastCheck.class,
            ForceTryCheck.class,
            PrintStatementCheck.class,
            TodoCommentCheck.class,
            HardcodedSecretCheck.class,
            InsecureUrlCheck.class,
            WeakCryptoMD5Check.class,
            // concurrency
            SendableViolationCheck.class,
            UncheckedSendableHotspotCheck.class,
            ActorIsolationViolationCheck.class,
            MainActorViolationCheck.class,
            TaskRetainCycleCheck.class,
            DetachedTaskMisuseCheck.class,
            MissingAwaitCheck.class,
            SharedMutableStateCheck.class,
            NonisolatedUnsafeHotspotCheck.class);
}
