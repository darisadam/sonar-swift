import Foundation
#if canImport(Darwin)
import Darwin
#elseif canImport(Glibc)
import Glibc
#endif

/// Lightweight in-process metrics for the parser sidecar. Read by the Java
/// plugin via `{"op":"metrics"}`; surfaced in `.sonar/leaks/latest.json` so
/// regressions in the *parser's* memory show up alongside JVM heap growth.
///
/// Thread-safety: all counters are wrapped in a single serial dispatch queue.
/// The parser process is single-threaded by default, but instruments may run
/// concurrently in the future.
enum Metrics {

    private static let queue = DispatchQueue(label: "io.sonarswift.parser.metrics")
    private static var filesParsed = 0
    private static var allocationsAtStart: Int64 = 0
    private static var hasCaptured = false

    /// Snapshot for the Java side. Returns a JSON-serializable dictionary.
    static func snapshot() -> [String: Any] {
        queue.sync {
            if !hasCaptured {
                allocationsAtStart = currentAllocations()
                hasCaptured = true
            }
        }

        let mem = currentMemoryUsage()
        let allocs = currentAllocations()
        return queue.sync {
            return [
                "timestampMillis": Int(Date().timeIntervalSince1970 * 1000),
                "residentBytes":   mem.resident,
                "virtualBytes":    mem.virtual,
                "liveThreads":     liveThreadCount(),
                "allocationsTotal":      allocs,
                "allocationsPerFile":    filesParsed == 0 ? 0 : (allocs - allocationsAtStart) / Int64(max(1, filesParsed)),
                "filesParsed":     filesParsed
            ]
        }
    }

    static func recordParse() {
        queue.sync { filesParsed += 1 }
    }

    // MARK: - Platform glue

    private static func currentMemoryUsage() -> (resident: Int64, virtual: Int64) {
        #if canImport(Darwin)
        var info = mach_task_basic_info()
        var count = mach_msg_type_number_t(MemoryLayout<mach_task_basic_info>.size / MemoryLayout<integer_t>.size)
        let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) { ptr in
            ptr.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(MACH_TASK_BASIC_INFO), $0, &count)
            }
        }
        if kerr == KERN_SUCCESS {
            return (Int64(info.resident_size), Int64(info.virtual_size))
        }
        return (-1, -1)
        #elseif canImport(Glibc)
        // /proc/self/status — VmRSS, VmSize
        guard let status = try? String(contentsOfFile: "/proc/self/status") else {
            return (-1, -1)
        }
        var rss: Int64 = -1
        var vsize: Int64 = -1
        for line in status.split(separator: "\n") {
            if line.hasPrefix("VmRSS:") {
                rss = parseProcKB(line) * 1024
            } else if line.hasPrefix("VmSize:") {
                vsize = parseProcKB(line) * 1024
            }
        }
        return (rss, vsize)
        #else
        return (-1, -1)
        #endif
    }

    #if canImport(Glibc)
    private static func parseProcKB(_ line: Substring) -> Int64 {
        let parts = line.split(whereSeparator: { $0 == " " || $0 == "\t" })
        // ["VmRSS:", "12345", "kB"]
        if parts.count >= 2, let n = Int64(parts[1]) {
            return n
        }
        return 0
    }
    #endif

    /// Total live allocations since process start, in bytes. Best-effort
    /// estimate based on Mach `task_vm_info` on macOS; falls back to the
    /// resident set when no allocation counter is available.
    private static func currentAllocations() -> Int64 {
        #if canImport(Darwin)
        var info = task_vm_info_data_t()
        var count = mach_msg_type_number_t(MemoryLayout<task_vm_info_data_t>.size / MemoryLayout<integer_t>.size)
        let kerr: kern_return_t = withUnsafeMutablePointer(to: &info) { ptr in
            ptr.withMemoryRebound(to: integer_t.self, capacity: Int(count)) {
                task_info(mach_task_self_, task_flavor_t(TASK_VM_INFO), $0, &count)
            }
        }
        if kerr == KERN_SUCCESS {
            return Int64(info.phys_footprint)
        }
        #endif
        return currentMemoryUsage().resident
    }

    private static func liveThreadCount() -> Int {
        #if canImport(Darwin)
        var threads: thread_act_array_t?
        var threadCount: mach_msg_type_number_t = 0
        let kerr = task_threads(mach_task_self_, &threads, &threadCount)
        if kerr == KERN_SUCCESS, let threads = threads {
            // Release the array so we don't leak in our own leak-detection code.
            vm_deallocate(mach_task_self_,
                          vm_address_t(bitPattern: threads),
                          vm_size_t(threadCount) * vm_size_t(MemoryLayout<thread_t>.size))
            return Int(threadCount)
        }
        return 0
        #else
        return ProcessInfo.processInfo.activeProcessorCount
        #endif
    }
}
