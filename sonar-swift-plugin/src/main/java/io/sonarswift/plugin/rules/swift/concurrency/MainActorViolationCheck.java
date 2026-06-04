package io.sonarswift.plugin.rules.swift.concurrency;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;
import java.util.Set;

/**
 * S1903 — Touch of UIKit / SwiftUI / AppKit from a likely non-main context.
 *
 * <p>Heuristic on tokens: identifier patterns like {@code self.someLabel.text =}
 * or {@code UIApplication.shared.X} when the enclosing function is declared
 * {@code async} but not marked {@code @MainActor}. Catches the most common
 * Swift Concurrency footgun: hopping into a `Task { ... }` and forgetting
 * to come back to main before touching UI.</p>
 *
 * <p>AST mode is much better here — see the matching check in the parser
 * sidecar. The token-based check fires only on a small allow-list of
 * obvious UIKit / SwiftUI APIs to keep false positives low.</p>
 */
public class MainActorViolationCheck extends SwiftCheck {

    public MainActorViolationCheck() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1903";
    }

    /** A small allow-list of high-signal UIKit / AppKit / SwiftUI receivers. */
    private static final Set<String> UI_RECEIVERS = Set.of(
            "UIApplication", "UIScreen", "UIViewController", "UIView",
            "NSApplication", "NSWindow", "NSView",
            "WKWebView", "UIWindow", "UIAlertController");

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        // Naive scope tracking: track whether we're inside an async func that is
        // NOT @MainActor. A token-level approximation; AST mode is precise.
        boolean inAsyncNonMain = false;
        int braceDepth = 0;
        int triggerDepth = -1;
        boolean atTopOfNextFunc = false;
        boolean lastSawMainActor = false;

        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            String text = t.text();

            if ("@MainActor".equals(text)) {
                lastSawMainActor = true;
                continue;
            }
            if (t.kind() == Token.Kind.KEYWORD && "func".equals(text)) {
                atTopOfNextFunc = true;
                continue;
            }
            if (atTopOfNextFunc && t.kind() == Token.Kind.KEYWORD && "async".equals(text)) {
                inAsyncNonMain = !lastSawMainActor;
                lastSawMainActor = false;
                atTopOfNextFunc = false;
            }
            if (atTopOfNextFunc && "{".equals(text)) {
                atTopOfNextFunc = false;
                lastSawMainActor = false;
            }

            if ("{".equals(text)) {
                braceDepth++;
                if (inAsyncNonMain && triggerDepth < 0) triggerDepth = braceDepth;
            }
            if ("}".equals(text)) {
                if (braceDepth == triggerDepth) {
                    inAsyncNonMain = false;
                    triggerDepth = -1;
                }
                braceDepth--;
            }

            if (!inAsyncNonMain) continue;
            if (t.kind() != Token.Kind.IDENT) continue;
            if (!UI_RECEIVERS.contains(text)) continue;

            // Confirm it's a member access (followed by `.`)
            if (i + 1 < tokens.size() && ".".equals(tokens.get(i + 1).text())) {
                reportIssue(ctx, file, t,
                        "UI API `" + text + "` is touched from an `async` context that is not "
                        + "`@MainActor`-isolated. Wrap the work in `await MainActor.run { ... }` "
                        + "or annotate the function `@MainActor`.");
            }
        }
    }
}
