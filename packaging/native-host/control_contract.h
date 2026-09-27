#pragma once
#include "engine_json.h"
#include <algorithm>

namespace xydesk::panel_control {
struct Endpoint { unsigned pid = 0; unsigned short port = 0; std::string token; };
inline std::optional<Endpoint> bootstrap(std::string_view frame, unsigned expectedPid) {
    if (frame.size() > 1024 || !expectedPid) return {};
    const auto o = engine_json::parse(frame);
    if (!o) return {};
    const auto number = [&](const char* key) -> std::int64_t {
        auto it = o->find(key); if (it == o->end()) return -1;
        auto n = std::get_if<std::int64_t>(&it->second); return n ? *n : -1;
    };
    const auto text = [&](const char* key) -> std::string {
        auto it = o->find(key); if (it == o->end()) return {};
        auto v = std::get_if<std::string>(&it->second); return v ? *v : "";
    };
    if (number("protocol") != 1 || number("pid") != expectedPid) return {};
    auto token = text("token"), url = text("url");
    const std::string prefix = "http://127.0.0.1:";
    if (token.size() != 32 || !std::all_of(token.begin(), token.end(), [](char c){return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');}) || url.rfind(prefix, 0) != 0) return {};
    const auto portText = url.substr(prefix.size());
    unsigned port = 0;
    const auto result = std::from_chars(portText.data(), portText.data()+portText.size(), port);
    if (result.ec != std::errc{} || result.ptr != portText.data()+portText.size() || port == 0 || port > 65535) return {};
    return Endpoint{expectedPid, static_cast<unsigned short>(port), token};
}
inline std::string quoteJson(std::string_view text) {
    const char* hex = "0123456789abcdef";
    std::string out = "\"";
    for (unsigned char c : text) {
        if (c == '"' || c == '\\') { out += '\\'; out += static_cast<char>(c); }
        else if (c < 32) { out += "\\u00"; out += hex[c >> 4]; out += hex[c & 15]; }
        else out += static_cast<char>(c);
    }
    return out + '"';
}
inline std::wstring deviceLink(const std::wstring& id) {
    if (id.size()!=9 || !std::all_of(id.begin(),id.end(),[](wchar_t c){return c>=L'0' && c<=L'9';})) return L"";
    return L"https://remote.xydesk.my.id/connect?device=" + id;
}
}
