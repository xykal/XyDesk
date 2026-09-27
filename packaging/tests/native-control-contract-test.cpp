#include "control_contract.h"
#include <cassert>
using namespace xydesk::panel_control;
int main(){
    const std::string token(32,'a');
    auto frame=[&](std::string url,int pid=123){return "{\"protocol\":1,\"pid\":"+std::to_string(pid)+",\"url\":"+quoteJson(url)+",\"token\":"+quoteJson(token)+"}\n";};
    auto valid=bootstrap(frame("http://127.0.0.1:43210"),123);
    assert(valid&&valid->port==43210&&valid->pid==123&&valid->token==token);
    assert(!bootstrap(frame("http://127.0.0.1:43210"),456));
    for(const auto* url:{"https://127.0.0.1:80","http://localhost:80","http://127.0.0.1:0","http://127.0.0.1:65536","http://127.0.0.1:80/path","http://127.0.0.1:80@evil.example","http://127.0.0.1:80?token=a"})assert(!bootstrap(frame(url),123));
    assert(!bootstrap(std::string(1025,' '),123));
    assert(!bootstrap("{}",123));
    auto bad=frame("http://127.0.0.1:80");bad.replace(bad.find(token),token.size(),"short");assert(!bootstrap(bad,123));
    for(const std::string value:{"A\"B\\C", "line\nbreak", "\xc3\xa9-test"}){
        auto parsed=xydesk::engine_json::parse("{\"password\":"+quoteJson(value)+"}");assert(parsed&&std::get<std::string>(parsed->at("password"))==value);
    }
    assert(deviceLink(L"123456789")==L"https://remote.xydesk.my.id/connect?device=123456789");
    for(const auto* invalid:{L"",L"123",L"123456789&password=x",L"123 456 789",L"12345678x"})assert(deviceLink(invalid).empty());
}
