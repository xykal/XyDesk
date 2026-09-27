#pragma once
// Kontrak IPC engine: objek datar, nilai string/bool/integer/null, UTF-8 sah.
// Bukan parser JSON umum. Nested object, float, duplicate key dan trailing
// garbage ditolak; batas input berlaku sebelum parsing/alokasi.
#include <charconv>
#include <cstddef>
#include <cstdint>
#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <variant>

namespace xydesk::engine_json {
using Scalar = std::variant<std::string, std::int64_t, bool, std::nullptr_t>;
using Object = std::map<std::string, Scalar>;

class Parser {
    std::string_view input;
    std::size_t at = 0;
    void space() {
        while (at < input.size() && (input[at] == ' ' || input[at] == '\t' || input[at] == '\r' || input[at] == '\n')) ++at;
    }
    bool take(char c) {
        space();
        if (at == input.size() || input[at] != c) return false;
        ++at; return true;
    }
    static void utf8(std::string& out, std::uint32_t cp) {
        if (cp <= 0x7f) out += static_cast<char>(cp);
        else if (cp <= 0x7ff) {
            out += static_cast<char>(0xc0 | (cp >> 6));
            out += static_cast<char>(0x80 | (cp & 63));
        } else if (cp <= 0xffff) {
            out += static_cast<char>(0xe0 | (cp >> 12));
            out += static_cast<char>(0x80 | ((cp >> 6) & 63));
            out += static_cast<char>(0x80 | (cp & 63));
        } else {
            out += static_cast<char>(0xf0 | (cp >> 18));
            out += static_cast<char>(0x80 | ((cp >> 12) & 63));
            out += static_cast<char>(0x80 | ((cp >> 6) & 63));
            out += static_cast<char>(0x80 | (cp & 63));
        }
    }
    std::optional<std::uint32_t> hex4() {
        if (input.size() - at < 4) return {};
        std::uint32_t value = 0;
        for (int i = 0; i < 4; ++i) {
            const char c = input[at++];
            int digit = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
            if (digit < 0) return {};
            value = value * 16 + static_cast<unsigned>(digit);
        }
        return value;
    }
    std::optional<std::string> text() {
        if (!take('"')) return {};
        std::string out;
        while (at < input.size()) {
            const auto c = static_cast<unsigned char>(input[at++]);
            if (c == '"') return out;
            if (c < 0x20) return {};
            if (c == '\\') {
                if (at == input.size()) return {};
                const char escape = input[at++];
                switch (escape) {
                case '"': out += '"'; break;
                case '\\': out += '\\'; break;
                case '/': out += '/'; break;
                case 'b': out += '\b'; break;
                case 'f': out += '\f'; break;
                case 'n': out += '\n'; break;
                case 'r': out += '\r'; break;
                case 't': out += '\t'; break;
                case 'u': {
                    auto cp = hex4();
                    if (!cp) return {};
                    if (*cp >= 0xd800 && *cp <= 0xdbff) {
                        if (input.substr(at, 2) != "\\u") return {};
                        at += 2;
                        auto low = hex4();
                        if (!low || *low < 0xdc00 || *low > 0xdfff) return {};
                        *cp = 0x10000 + ((*cp - 0xd800) << 10) + (*low - 0xdc00);
                    } else if (*cp >= 0xdc00 && *cp <= 0xdfff) return {};
                    utf8(out, *cp); break;
                }
                default: return {};
                }
            } else if (c < 0x80) out += static_cast<char>(c);
            else {
                unsigned count;
                std::uint32_t cp, minimum;
                if (c >= 0xc2 && c <= 0xdf) { count = 1; cp = c & 31; minimum = 0x80; }
                else if (c >= 0xe0 && c <= 0xef) { count = 2; cp = c & 15; minimum = 0x800; }
                else if (c >= 0xf0 && c <= 0xf4) { count = 3; cp = c & 7; minimum = 0x10000; }
                else return {};
                if (input.size() - at < count) return {};
                for (unsigned i = 0; i < count; ++i) {
                    const auto next = static_cast<unsigned char>(input[at++]);
                    if ((next & 0xc0) != 0x80) return {};
                    cp = (cp << 6) | (next & 63);
                }
                if (cp < minimum || cp > 0x10ffff || (cp >= 0xd800 && cp <= 0xdfff)) return {};
                utf8(out, cp);
            }
        }
        return {};
    }
    std::optional<Scalar> scalar() {
        space();
        if (at == input.size()) return {};
        if (input[at] == '"') {
            auto value = text();
            if (value) return Scalar{*value};
            return {};
        }
        if (input.substr(at, 4) == "true") { at += 4; return Scalar{true}; }
        if (input.substr(at, 5) == "false") { at += 5; return Scalar{false}; }
        if (input.substr(at, 4) == "null") { at += 4; return Scalar{nullptr}; }
        const auto start = at;
        if (input[at] == '-') ++at;
        const auto digits = at;
        while (at < input.size() && input[at] >= '0' && input[at] <= '9') ++at;
        if (at == digits || (at - digits > 1 && input[digits] == '0')) return {};
        std::int64_t number = 0;
        const auto result = std::from_chars(input.data() + start, input.data() + at, number);
        if (result.ec != std::errc{} || result.ptr != input.data() + at) return {};
        return Scalar{number};
    }
public:
    explicit Parser(std::string_view json) : input(json) {}
    std::optional<Object> parse() {
        if (input.size() > 16384 || !take('{')) return {};
        Object object;
        if (!take('}')) {
            do {
                auto key = text();
                if (!key || !take(':')) return {};
                auto value = scalar();
                if (!value || !object.emplace(*key, *value).second) return {};
                if (take('}')) break;
                if (!take(',')) return {};
            } while (true);
        }
        space();
        if (at != input.size()) return {};
        return object;
    }
};
inline std::optional<Object> parse(std::string_view json) { return Parser(json).parse(); }
} // namespace xydesk::engine_json
