import Foundation
import zlib

/// Bounded in-memory ZIP reader/writer for XLSX; never extracts paths to disk.
struct ZipContainer {
    static func read(_ data: Data) throws -> [String: Data] {
        let b = [UInt8](data)
        func u16(_ i: Int) throws -> Int { guard i >= 0, i + 2 <= b.count else { throw PocketError.message("ZIP 文件损坏") }; return Int(b[i]) | Int(b[i+1]) << 8 }
        func u32(_ i: Int) throws -> Int { try u16(i) | (u16(i+2) << 16) }
        guard b.count >= 22, b.count <= 30_000_000 else { throw PocketError.message("文件过大或不是有效 XLSX") }
        guard let end = stride(from: b.count-22, through: max(0,b.count-65557), by: -1).first(where: { (try? u32($0)) == 0x06054b50 && (try? u16($0+20)) == b.count-$0-22 }) else { throw PocketError.message("找不到 ZIP 目录") }
        let count = try u16(end+10)
        guard count <= 2000, try u16(end+4) == 0, try u16(end+6) == 0 else { throw PocketError.message("不支持此 ZIP 格式") }
        var pos = try u32(end+16), result: [String:Data] = [:], total = 0
        for _ in 0..<count {
            guard try u32(pos) == 0x02014b50 else { throw PocketError.message("ZIP 目录损坏") }
            let flags = try u16(pos+8), method = try u16(pos+10), expectedCRC = try u32(pos+16)
            let compressed = try u32(pos+20), size = try u32(pos+24), n = try u16(pos+28), extra = try u16(pos+30), comment = try u16(pos+32), local = try u32(pos+42)
            total += size
            guard flags & 1 == 0, size <= 15_000_000, total <= 50_000_000, pos+46+n+extra+comment <= b.count else { throw PocketError.message("加密或过大的 XLSX 不受支持") }
            let name = String(decoding:b[(pos+46)..<(pos+46+n)],as:UTF8.self)
            guard try u32(local) == 0x04034b50 else { throw PocketError.message("ZIP 文件头损坏") }
            let start = try local+30+u16(local+26)+u16(local+28)
            guard start >= 0, compressed >= 0, start+compressed <= b.count else { throw PocketError.message("ZIP 数据越界") }
            var output: Data
            if method == 0 { output = Data(b[start..<start+compressed]); guard output.count == size else { throw PocketError.message("ZIP 大小不符") } }
            else if method == 8 {
                var source = Array(b[start..<start+compressed]), dest = [UInt8](repeating:0,count:max(1,size))
                var stream = z_stream()
                guard inflateInit2_(&stream,-MAX_WBITS,ZLIB_VERSION,Int32(MemoryLayout<z_stream>.size)) == Z_OK else { throw PocketError.message("解压初始化失败") }
                let status = source.withUnsafeMutableBufferPointer { src in dest.withUnsafeMutableBufferPointer { dst in
                    stream.next_in = src.baseAddress; stream.avail_in = uInt(compressed)
                    stream.next_out = dst.baseAddress; stream.avail_out = uInt(max(1,size))
                    return inflate(&stream,Z_FINISH)
                }}
                let written = stream.total_out
                inflateEnd(&stream)
                guard status == Z_STREAM_END, written == size else { throw PocketError.message("XLSX 解压失败") }
                output = Data(dest.prefix(size))
            } else { throw PocketError.message("不支持的 ZIP 压缩格式") }
            let crc = output.withUnsafeBytes { crc32(0,$0.bindMemory(to:Bytef.self).baseAddress,uInt(output.count)) }
            guard crc == expectedCRC else { throw PocketError.message("ZIP 校验失败") }
            guard result[name] == nil else { throw PocketError.message("ZIP 有重复文件") }
            result[name] = output
            pos += 46+n+extra+comment
        }
        return result
    }
    static func write(_ files: [(String,Data)]) -> Data {
        var data = Data(), directory = Data()
        func number(_ value: Int, _ bytes: Int) -> Data { Data((0..<bytes).map { UInt8(truncatingIfNeeded:value >> ($0*8)) }) }
        for (name,body) in files {
            let nameData = Data(name.utf8), offset = data.count
            let crc = Int(body.withUnsafeBytes { crc32(0,$0.bindMemory(to:Bytef.self).baseAddress,uInt(body.count)) })
            data += number(0x04034b50,4)
            data += number(20,2)
            data += number(0,2)
            data += number(0,2)
            data += number(0,4)
            data += number(crc,4)
            data += number(body.count,4)
            data += number(body.count,4)
            data += number(nameData.count,2)
            data += number(0,2)
            data += nameData
            data += body
            directory += number(0x02014b50,4)
            directory += number(20,2)
            directory += number(20,2)
            directory += number(0,2)
            directory += number(0,2)
            directory += number(0,4)
            directory += number(crc,4)
            directory += number(body.count,4)
            directory += number(body.count,4)
            directory += number(nameData.count,2)
            directory += number(0,2)
            directory += number(0,2)
            directory += number(0,2)
            directory += number(0,2)
            directory += number(0,4)
            directory += number(offset,4)
            directory += nameData
        }
        let offset = data.count; data += directory
        data += number(0x06054b50,4)
        data += number(0,2)
        data += number(0,2)
        data += number(files.count,2)
        data += number(files.count,2)
        data += number(directory.count,4)
        data += number(offset,4)
        data += number(0,2)
        return data
    }
}
