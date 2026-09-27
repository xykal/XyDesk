#include "engine_json.h"
#include <cassert>
#include <string>
using xydesk::engine_json::parse;
int main() {
    const auto identity = parse(R"({ "deviceId": "123456789", "password": "Kopi\"\\\u00e9\ud83d\udd12" })");
    assert(identity);
    assert(std::get<std::string>(identity->at("deviceId")) == "123456789");
    assert(std::get<std::string>(identity->at("password")) == "Kopi\"\\\xc3\xa9\xf0\x9f\x94\x92");
    const auto literal = parse("{\"password\":\"\xc3\xa9\xf0\x9f\x94\x92\"}");
    assert(literal);
    assert(std::get<std::string>(literal->at("password")) == "\xc3\xa9\xf0\x9f\x94\x92");
    const auto status = parse(R"({"pid":123,"armed":true,"black_frames":false,"proc_session":null,"started_at_ms":1790000000000,"state":"ready"})");
    assert(status && std::get<std::int64_t>(status->at("pid")) == 123);
    assert(std::get<bool>(status->at("armed")));
    assert(std::holds_alternative<std::nullptr_t>(status->at("proc_session")));
    for (const char* invalid : {
        R"({"password":"abc})", R"({"password":"\q"})", R"({"password":"\ud800"})",
        R"({"password":"\udc00"})", R"({"password":"\ud800\u0041"})", R"({"password":"\u12"})",
        R"({"password":"a","password":"b"})", R"({"password":"a"} garbage)",
        R"({"pid":01})", R"({"pid":9223372036854775808})", R"({"pid":1.5})",
        R"({"pid":1,})", R"({"pid":})", R"({"nested":{"password":"a"}})",
        "{\"password\":\"\xc0\xaf\"}", "{\"password\":\"\xed\xa0\x80\"}",
        "{\"password\":\"\xf4\x90\x80\x80\"}", "{\"password\":\"line\nbreak\"}"
    }) assert(!parse(invalid));
    assert(!parse(std::string(16385, ' ')));
    assert(parse("{}"));
    return 0;
}
