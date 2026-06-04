package io.sonarswift.plugin.rules.swift;

import io.sonarswift.plugin.SwiftPluginConstants;
import io.sonarswift.plugin.parser.Token;
import io.sonarswift.plugin.rules.SwiftCheck;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;

import java.util.List;
import java.util.Set;

/**
 * S1804 — Use of MD5 for any non-checksum purpose. Flags:
 *
 * <ul>
 *   <li>{@code CC_MD5(...)} — CommonCrypto low-level call</li>
 *   <li>{@code Insecure.MD5.hash(data:)} — CryptoKit's deliberately-named insecure MD5</li>
 *   <li>{@code Insecure.MD5()} construction</li>
 *   <li>{@code MD5Digest} type reference</li>
 * </ul>
 *
 * <p>If used only for checksums / file-fingerprinting on trusted input,
 * suppress per-line with {@code // sonar-disable-next-line:S1804}.</p>
 */
public class WeakCryptoMD5Check extends SwiftCheck {

    private static final Set<String> SUSPECT_IDENTS = Set.of(
            "CC_MD5", "MD5Digest");

    public WeakCryptoMD5Check() {
        super(SwiftPluginConstants.SWIFT_REPOSITORY_KEY);
    }

    @Override
    public String ruleKey() {
        return "S1804";
    }

    @Override
    public void visitTokens(InputFile file, List<Token> tokens, SensorContext ctx) {
        for (int i = 0; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.kind() != Token.Kind.IDENT) continue;
            if (SUSPECT_IDENTS.contains(t.text())) {
                reportIssue(ctx, file, t,
                        "MD5 is cryptographically broken — use SHA-256 / SHA-3 / BLAKE2 instead.");
                continue;
            }
            // Insecure.MD5
            if ("Insecure".equals(t.text())
                    && i + 2 < tokens.size()
                    && ".".equals(tokens.get(i + 1).text())
                    && "MD5".equals(tokens.get(i + 2).text())) {
                reportIssue(ctx, file, t,
                        "MD5 is cryptographically broken — use SHA-256 / SHA-3 / BLAKE2 instead.");
            }
        }
    }
}
