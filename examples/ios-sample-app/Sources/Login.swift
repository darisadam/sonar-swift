// Deliberately bug-ridden sample to exercise the scanner. Every numbered
// comment refers to a rule the plugin should flag.

import Foundation
import CryptoKit

struct API {
    // S1800 — hardcoded API token (catalog pattern: AWS access key shape)
    static let key = "AKIAIOSFODNN7EXAMPLE"

    // S1802 — cleartext http:// URL
    static let baseURL = "http://api.example.com/login"
}

final class LoginService {

    func login(user: String, password: String) {
        // S1001 — force-unwrap
        let url = URL(string: API.baseURL)!

        // S1004 — force-try
        let data = try! JSONSerialization.data(withJSONObject: ["u": user, "p": password])

        // S1804 — MD5
        let token = Insecure.MD5.hash(data: data)

        // S1005 — print() left in production code
        print("Sending login for \(user) with token \(Array(token))")

        // TODO: implement real network call    (S1007)

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.httpBody = data

        URLSession.shared.dataTask(with: req) { responseData, _, _ in
            // S1003 — force-cast
            let dict = try! JSONSerialization.jsonObject(with: responseData!) as! [String: Any]
            // S1001 — force-unwrap again
            let session = dict["session"]!
            print(session)
        }.resume()
    }
}
