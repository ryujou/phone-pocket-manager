import Foundation
struct ArchiveFile {
    let url: URL
    func load() throws -> Archive {
        guard FileManager.default.fileExists(atPath: url.path) else { return Archive() }
        return try JSONDecoder().decode(Archive.self, from: Data(contentsOf: url))
    }
    func save(_ archive: Archive) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        let data = try JSONEncoder().encode(archive)
        try data.write(to: url, options: .atomic)
    }
}
