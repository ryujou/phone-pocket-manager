import Foundation
#if canImport(FoundationXML)
import FoundationXML
#endif
final class SheetXML: NSObject, XMLParserDelegate {
    var rows: [[String]] = [], strings: [String] = [], links: [String:String] = [:], sheetIDs: [String] = []
    private var row: [String] = [], text = "", cell = "", type = "", column = 0, inText = false, shared = ""
    func parse(_ data: Data) throws {
        let parser = XMLParser(data:data); parser.delegate = self; parser.shouldResolveExternalEntities = false
        guard parser.parse() else { throw PocketError.message("Excel XML 无法解析") }
    }
    func parser(_ parser: XMLParser, didStartElement name: String, namespaceURI: String?, qualifiedName: String?, attributes: [String:String]) {
        if name == "row" { row = [] }
        if name == "c" {
            cell = ""; type = attributes["t"] ?? ""; column = 0
            for scalar in (attributes["r"] ?? "A").unicodeScalars where (65...90).contains(scalar.value) { column = column*26+Int(scalar.value)-64 }
            column = max(0,column-1)
        }
        if name == "si" { shared = "" }
        if name == "t" || name == "v" { inText = true; text = "" }
        if name == "Relationship", let id = attributes["Id"], let target = attributes["Target"] { links[id] = target }
        if name == "sheet", let id = attributes["r:id"] { sheetIDs.append(id) }
    }
    func parser(_ parser: XMLParser, foundCharacters string: String) { if inText { text += string } }
    func parser(_ parser: XMLParser, didEndElement name: String, namespaceURI: String?, qualifiedName: String?) {
        if name == "t" || name == "v" { inText = false; cell += text; if name == "t" { shared += text } }
        if name == "si" { strings.append(shared) }
        if name == "c", column < 256 {
            while row.count <= column { row.append("") }
            row[column] = type == "s" ? (Int(cell).flatMap { strings.indices.contains($0) ? strings[$0] : nil } ?? "") : cell
        }
        if name == "row" { rows.append(row) }
    }
}
