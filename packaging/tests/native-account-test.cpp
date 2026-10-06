#define NOMINMAX
#include "account_auth.h"
#include "session_view.h"
#include <cassert>
#include <iostream>
using namespace xydesk::account;
int main(){
 // RFC 7636 Appendix B; no network, real accounts or Credential Manager writes.
 const std::string verifier="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
 assert(challenge(verifier)=="E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM");
 auto a=xydesk::account::random(),b=xydesk::account::random();assert(a.size()==43&&b.size()==43&&a!=b);
 assert(constantEqual(a,a)&&!constantEqual(a,b)&&!constantEqual(a,a+"x"));
 assert(escape("http://127.0.0.1:1234/")=="http%3A%2F%2F127.0.0.1%3A1234%2F");
 auto valid=callback("GET /?code=one%2Btwo&state=expected HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n");assert(valid&&valid->at("code")=="one+two"&&valid->at("state")=="expected");
 assert(!callback("GET /?state=first&state=second HTTP/1.1\r\n\r\n"));
 assert(!callback("GET /?state=%0d%0a HTTP/1.1\r\n\r\n"));
 assert(!callback("POST /?code=x&state=y HTTP/1.1\r\n\r\n"));
 assert(!callback("GET /elsewhere?code=x&state=y HTTP/1.1\r\n\r\n"));
 assert(!validToken(std::string(40,'a')+"\r\nInjected: true"));assert(!validToken(std::string(CRED_MAX_CREDENTIAL_BLOB_SIZE+1,'a')));
 auto user=profile(nlohmann::json{{"user",{{"name","Fixture"},{"email","fixture@example.invalid"}}}});assert(user.ok&&user.name==L"Fixture");
 assert(!profile(nlohmann::json{{"user",{{"email",12}}}}).ok);
 auto noSession=xydesk::session_view::parse(R"({"session":null,"password":"NEVER DISPLAY"})");assert(noSession.known&&!noSession.active);
 auto active=xydesk::session_view::parse(R"({"session":{"clientId":"fixture","clientName":"Redmi Note 12","clientPlatform":"android","durationMs":125000},"password":"NEVER DISPLAY"})");assert(active.active&&active.name==L"Redmi Note 12"&&active.seconds==125);
 assert(!xydesk::session_view::parse(R"({"session":false})").known);
 // Tawaran berkas masuk yang menunggu jawaban pemilik PC.
 auto pending=xydesk::session_view::parse(R"({"session":null,"pendingFile":{"id":42,"name":"pasang.exe","size":2048,"risky":true,"secondsLeft":51}})");
 assert(pending.known&&pending.pendingId==42&&pending.pendingName==L"pasang.exe"&&pending.pendingSize==2048&&pending.pendingRisky&&pending.pendingSeconds==51);
 // Tanpa tawaran, dan dengan tawaran rusak, panel tidak boleh mengira ada dialog.
 assert(xydesk::session_view::parse(R"({"session":null,"pendingFile":null})").pendingId==0);
 assert(xydesk::session_view::parse(R"({"session":null,"pendingFile":{"name":"x"}})").pendingId==0);
 assert(!xydesk::session_view::parse(R"({"session":null,"pendingFile":{"id":7,"name":"x.txt","size":10}})").pendingRisky);
 assert(xydesk::session_view::redmiNote12(L"23021RAAEG · Fixture"));assert(!xydesk::session_view::redmiNote12(L"Redmi Note 12 Pro"));assert(!xydesk::session_view::redmiNote12(L"Chrome di Android"));
 std::cout<<"Native account PKCE, randomness, callback parsing, profile and header safety: passed\n";
}
