// A second file used to demonstrate duplications + complexity metrics.

import Foundation

struct Profile {
    let id: String
    let displayName: String
    let avatarURL: URL?
}

final class ProfileFormatter {

    func summarize(_ profile: Profile) -> String {
        var parts: [String] = []
        if !profile.displayName.isEmpty {
            parts.append(profile.displayName)
        } else {
            parts.append("Anon")
        }
        if let url = profile.avatarURL {
            parts.append(url.absoluteString)
        } else {
            parts.append("no-avatar")
        }
        return parts.joined(separator: " · ")
    }

    func summarizeDuplicate(_ profile: Profile) -> String {
        // Deliberately duplicated of summarize() to trigger CPD.
        var parts: [String] = []
        if !profile.displayName.isEmpty {
            parts.append(profile.displayName)
        } else {
            parts.append("Anon")
        }
        if let url = profile.avatarURL {
            parts.append(url.absoluteString)
        } else {
            parts.append("no-avatar")
        }
        return parts.joined(separator: " · ")
    }
}
