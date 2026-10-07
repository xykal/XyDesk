#pragma once
// UI account only. Does not change engine identity, transport or host ownership.
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#include <winsock2.h>
#include <windows.h>
#include <winhttp.h>
#include <wincred.h>
#include <bcrypt.h>
#include <shellapi.h>
#include <atomic>
#include <array>
#include <optional>
#include <algorithm>
#include <string>
#include <map>
#include "vendor/json/json.hpp"
#pragma comment(lib,"ws2_32.lib")
#pragma comment(lib,"bcrypt.lib")
#pragma comment(lib,"advapi32.lib")
#pragma comment(lib,"winhttp.lib")
#pragma comment(lib,"shell32.lib")
namespace xydesk::account {
// Public installed-app OAuth client, matches cloudflare/wrangler.toml; NOT a secret.
constexpr char clientId[]="335906355717-r2em6iirn8uv39qo6ol9iti8ijcv0et8.apps.googleusercontent.com";
constexpr wchar_t credentialName[]=L"XyDesk/NativeAccount/v1";
struct Result { bool ok=false; std::wstring name,email,message; };
inline std::wstring wide(const std::string& value){int n=MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,value.data(),static_cast<int>(value.size()),nullptr,0);if(!n)return {};std::wstring out(n,0);MultiByteToWideChar(CP_UTF8,MB_ERR_INVALID_CHARS,value.data(),static_cast<int>(value.size()),out.data(),n);return out;}
inline std::string base64url(const unsigned char* data,size_t size){const char* chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";std::string out;unsigned value=0;int bits=0;for(size_t i=0;i<size;++i){value=(value<<8)|data[i];bits+=8;while(bits>=6){bits-=6;out+=chars[(value>>bits)&63];}}if(bits)out+=chars[(value<<(6-bits))&63];return out;}
inline std::string random(){std::array<unsigned char,32> bytes{};if(BCryptGenRandom(nullptr,bytes.data(),static_cast<ULONG>(bytes.size()),BCRYPT_USE_SYSTEM_PREFERRED_RNG)!=0)return {};return base64url(bytes.data(),bytes.size());}
inline std::string challenge(const std::string& verifier){BCRYPT_ALG_HANDLE alg=nullptr;std::array<unsigned char,32> digest{};if(BCryptOpenAlgorithmProvider(&alg,BCRYPT_SHA256_ALGORITHM,nullptr,0)!=0)return {};auto status=BCryptHash(alg,nullptr,0,reinterpret_cast<PUCHAR>(const_cast<char*>(verifier.data())),static_cast<ULONG>(verifier.size()),digest.data(),static_cast<ULONG>(digest.size()));BCryptCloseAlgorithmProvider(alg,0);return status==0?base64url(digest.data(),digest.size()):"";}
inline std::string escape(const std::string& s){const char* hex="0123456789ABCDEF";std::string out;for(unsigned char c:s){if((c>='a'&&c<='z')||(c>='A'&&c<='Z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.'||c=='~')out+=static_cast<char>(c);else{out+='%';out+=hex[c>>4];out+=hex[c&15];}}return out;}
inline bool constantEqual(const std::string& a,const std::string& b){if(a.size()!=b.size())return false;unsigned diff=0;for(size_t i=0;i<a.size();++i)diff|=static_cast<unsigned char>(a[i]^b[i]);return diff==0;}
inline std::optional<std::map<std::string,std::string>> callback(const std::string& request){
 if(request.rfind("GET /?",0)!=0)return {};
 const auto end=request.find(" HTTP/1.1\r\n");if(end==std::string::npos||end>8192)return {};
 auto decode=[](const std::string& s)->std::optional<std::string>{std::string out;const auto hex=[](char c){if(c>='0'&&c<='9')return c-'0';if(c>='a'&&c<='f')return c-'a'+10;if(c>='A'&&c<='F')return c-'A'+10;return -1;};for(size_t i=0;i<s.size();++i){if(s[i]=='%'){if(i+2>=s.size())return {};int a=hex(s[i+1]),b=hex(s[i+2]);if(a<0||b<0)return {};char c=static_cast<char>((a<<4)|b);if(static_cast<unsigned char>(c)<32||c==127)return {};out+=c;i+=2;}else if(static_cast<unsigned char>(s[i])<32)return {};else out+=s[i]=='+'?' ':s[i];}return out;};
 std::map<std::string,std::string> fields;size_t pos=6;
 while(pos<end){size_t stop=request.find('&',pos);if(stop==std::string::npos||stop>end)stop=end;const auto part=request.substr(pos,stop-pos);const auto eq=part.find('=');if(eq==std::string::npos)return {};auto key=decode(part.substr(0,eq)),value=decode(part.substr(eq+1));if(!key||!value||!fields.emplace(*key,*value).second)return {};pos=stop+1;}return fields;
}
inline bool validToken(const std::string& token){return token.size()>20&&token.size()<=CRED_MAX_CREDENTIAL_BLOB_SIZE&&std::all_of(token.begin(),token.end(),[](char c){return (c>='A'&&c<='Z')||(c>='a'&&c<='z')||(c>='0'&&c<='9')||c=='-'||c=='_'||c=='.';});}
struct Http { HINTERNET value;explicit Http(HINTERNET h):value(h){}~Http(){if(value)WinHttpCloseHandle(value);}Http(const Http&)=delete; };
struct Response { long status=0; nlohmann::json body; };
// Satu jalur HTTP untuk semua panggilan akun. Mengembalikan status apa adanya
// supaya pemanggil bisa membedakan "ditolak dengan alasan" (400/401/429 —
// badannya berisi kode galat yang layak ditampilkan) dari "tidak nyambung"
// (status 0). Alur OTP butuh perbedaan itu; alur Google tidak, jadi wrapper
// `request()` di bawah tetap mengembalikan JSON kosong untuk apa pun selain
// 200 seperti sebelumnya.
inline Response requestFull(const wchar_t* method,const wchar_t* path,const std::string& body={},const std::string& token={}){
 Response out{};
 Http session(WinHttpOpen(L"XyDesk native account",WINHTTP_ACCESS_TYPE_AUTOMATIC_PROXY,WINHTTP_NO_PROXY_NAME,WINHTTP_NO_PROXY_BYPASS,0));if(!session.value)return out;
 WinHttpSetTimeouts(session.value,3000,5000,5000,5000);Http connection(WinHttpConnect(session.value,L"signal.xydesk.my.id",INTERNET_DEFAULT_HTTPS_PORT,0));if(!connection.value)return out;
 Http req(WinHttpOpenRequest(connection.value,method,path,nullptr,WINHTTP_NO_REFERER,WINHTTP_DEFAULT_ACCEPT_TYPES,WINHTTP_FLAG_SECURE));if(!req.value)return out;
 DWORD redirect=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;if(!WinHttpSetOption(req.value,WINHTTP_OPTION_REDIRECT_POLICY,&redirect,sizeof(redirect)))return out;
 std::wstring headers=L"Content-Type: application/json\r\n";if(!token.empty()){if(!validToken(token))return out;headers+=L"Authorization: Bearer "+wide(token)+L"\r\n";}
 if(!WinHttpSendRequest(req.value,headers.c_str(),static_cast<DWORD>(headers.size()),body.empty()?WINHTTP_NO_REQUEST_DATA:const_cast<char*>(body.data()),static_cast<DWORD>(body.size()),static_cast<DWORD>(body.size()),0)||!WinHttpReceiveResponse(req.value,nullptr))return out;
 DWORD code=0,size=sizeof(code);if(!WinHttpQueryHeaders(req.value,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&code,&size,WINHTTP_NO_HEADER_INDEX))return out;
 out.status=static_cast<long>(code);
 std::string response;auto deadline=GetTickCount64()+10000;while(GetTickCount64()<deadline){char data[2048];DWORD n=0;if(!WinHttpReadData(req.value,data,sizeof(data),&n))return out;if(!n){auto parsed=nlohmann::json::parse(response,nullptr,false);SecureZeroMemory(response.data(),response.size());if(!parsed.is_discarded())out.body=parsed;return out;}if(response.size()+n>65536)return out;response.append(data,n);}
 return out;
}
inline nlohmann::json request(const wchar_t* method,const wchar_t* path,const std::string& body={},const std::string& token={}){
 auto response=requestFull(method,path,body,token);
 return response.status==200?response.body:nlohmann::json{};
}
inline Result profile(const nlohmann::json& data){if(!data.is_object()||!data.contains("user")||!data["user"].is_object())return {false,{},{},L"Sesi akun belum dapat diverifikasi."};const auto& user=data["user"];if(!user.contains("email")||!user["email"].is_string())return {};auto email=user["email"].get<std::string>();auto name=user.contains("name")&&user["name"].is_string()?user["name"].get<std::string>():email;return {true,wide(name.substr(0,200)),wide(email.substr(0,254)),L"Akun terverifikasi. Identitas host Windows tetap terpisah."};}
inline bool signOut(){return CredDeleteW(credentialName,CRED_TYPE_GENERIC,0)||GetLastError()==ERROR_NOT_FOUND;}
inline Result restore(){PCREDENTIALW credential=nullptr;if(!CredReadW(credentialName,CRED_TYPE_GENERIC,0,&credential))return {false,{},{},L"Belum masuk akun XyDesk."};std::string token(reinterpret_cast<char*>(credential->CredentialBlob),credential->CredentialBlobSize);CredFree(credential);auto result=profile(request(L"GET",L"/auth/me",{},token));SecureZeroMemory(token.data(),token.size());return result;}
struct Socket {SOCKET value=INVALID_SOCKET;~Socket(){if(value!=INVALID_SOCKET)closesocket(value);}Socket(const Socket&)=delete;explicit Socket(SOCKET s):value(s){} };
inline Result login(std::atomic_bool& cancelled){
 auto fail=[](const wchar_t* message){return Result{false,{},{},message};};
 WSADATA data{};if(WSAStartup(MAKEWORD(2,2),&data)!=0)return fail(L"Jaringan login tidak dapat dimulai.");
 struct Cleanup{~Cleanup(){WSACleanup();}} cleanup;
 Socket listener(socket(AF_INET,SOCK_STREAM,IPPROTO_TCP));if(listener.value==INVALID_SOCKET)return fail(L"Callback privat tidak tersedia.");
 BOOL exclusive=TRUE;setsockopt(listener.value,SOL_SOCKET,SO_EXCLUSIVEADDRUSE,reinterpret_cast<const char*>(&exclusive),sizeof(exclusive));
 sockaddr_in addr{};addr.sin_family=AF_INET;addr.sin_addr.s_addr=htonl(INADDR_LOOPBACK);addr.sin_port=0;
 if(bind(listener.value,reinterpret_cast<sockaddr*>(&addr),sizeof(addr))==SOCKET_ERROR||listen(listener.value,4)==SOCKET_ERROR)return fail(L"Port callback privat tidak tersedia.");int len=sizeof(addr);if(getsockname(listener.value,reinterpret_cast<sockaddr*>(&addr),&len)!=0)return fail(L"Alamat callback tidak tersedia.");
 const auto verifier=random(),state=random(),hash=challenge(verifier);if(verifier.empty()||state.empty()||hash.empty())return fail(L"Generator keamanan login tidak tersedia.");
 const std::string redirect="http://127.0.0.1:"+std::to_string(ntohs(addr.sin_port))+"/";
 const auto url=wide("https://accounts.google.com/o/oauth2/v2/auth?client_id="+escape(clientId)+"&redirect_uri="+escape(redirect)+"&response_type=code&scope=openid%20email%20profile&code_challenge_method=S256&code_challenge="+escape(hash)+"&state="+escape(state)+"&prompt=select_account");
 if(cancelled)return fail(L"Login dibatalkan.");if(reinterpret_cast<INT_PTR>(ShellExecuteW(nullptr,L"open",url.c_str(),nullptr,nullptr,SW_SHOWNORMAL))<=32)return fail(L"Browser tidak dapat dibuka.");
 const auto deadline=GetTickCount64()+180000;
 while(!cancelled&&GetTickCount64()<deadline){
  fd_set ready;FD_ZERO(&ready);FD_SET(listener.value,&ready);timeval wait{0,200000};if(select(0,&ready,nullptr,nullptr,&wait)<=0)continue;
  Socket client(accept(listener.value,nullptr,nullptr));if(client.value==INVALID_SOCKET)continue;DWORD timeout=200;setsockopt(client.value,SOL_SOCKET,SO_RCVTIMEO,reinterpret_cast<char*>(&timeout),sizeof(timeout));setsockopt(client.value,SOL_SOCKET,SO_SNDTIMEO,reinterpret_cast<char*>(&timeout),sizeof(timeout));
  std::string raw;const auto readDeadline=GetTickCount64()+2000;while(!cancelled&&GetTickCount64()<readDeadline&&raw.size()<8192&&raw.find("\r\n\r\n")==std::string::npos){char part[1024];int n=recv(client.value,part,sizeof(part),0);if(n<=0)break;raw.append(part,n);}
  const auto fields=callback(raw);const bool valid=fields&&fields->count("state")&&constantEqual(fields->at("state"),state);
  const std::string response=valid?"HTTP/1.1 200 OK\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\nConnection: close\r\n\r\nKode diterima. Kembali ke XyDesk untuk melihat hasil login.":"HTTP/1.1 400 Bad Request\r\nCache-Control: no-store\r\nConnection: close\r\n\r\nCallback tidak valid.";
  send(client.value,response.data(),static_cast<int>(response.size()),0);shutdown(client.value,SD_BOTH);closesocket(client.value);client.value=INVALID_SOCKET;
  if(!valid)continue;if(cancelled)return fail(L"Login dibatalkan.");
  if(!fields->count("code")||fields->at("code").empty())return fail(L"Login tidak disetujui. Lu bisa mencoba lagi.");
  const auto body=nlohmann::json{{"code",fields->at("code")},{"code_verifier",verifier},{"redirect_uri",redirect}}.dump();auto result=request(L"POST",L"/auth/google/desktop",body);
  if(cancelled)return fail(L"Login dibatalkan.");auto user=profile(result);if(!user.ok||!result.contains("token")||!result["token"].is_string())return fail(L"Login belum berhasil. Periksa koneksi atau konfigurasi Google desktop.");
  auto token=result["token"].get<std::string>();if(!validToken(token))return fail(L"Token akun ditolak.");CREDENTIALW credential{};credential.Type=CRED_TYPE_GENERIC;credential.TargetName=const_cast<wchar_t*>(credentialName);credential.Persist=CRED_PERSIST_LOCAL_MACHINE;credential.CredentialBlobSize=static_cast<DWORD>(token.size());credential.CredentialBlob=reinterpret_cast<BYTE*>(token.data());credential.UserName=const_cast<wchar_t*>(user.email.c_str());
  const bool saved=CredWriteW(&credential,0)!=FALSE;SecureZeroMemory(token.data(),token.size());if(!saved)return fail(L"Windows tidak dapat menyimpan sesi dengan aman. Login belum disimpan.");return user;
 }
 return fail(cancelled?L"Login dibatalkan.":L"Waktu login habis. Coba lagi dari aplikasi.");
}

// ── Simpan sesi ke Credential Manager ───────────────────────────────────────
// Dipakai oleh login Google dan login email; satu tempat supaya aturan
// penyimpanannya (mesin lokal, blob di-nol-kan setelah ditulis) tidak bercabang.
inline bool storeSession(std::string& token,const std::wstring& email){
 if(!validToken(token))return false;
 CREDENTIALW credential{};credential.Type=CRED_TYPE_GENERIC;credential.TargetName=const_cast<wchar_t*>(credentialName);credential.Persist=CRED_PERSIST_LOCAL_MACHINE;credential.CredentialBlobSize=static_cast<DWORD>(token.size());credential.CredentialBlob=reinterpret_cast<BYTE*>(token.data());credential.UserName=const_cast<wchar_t*>(email.c_str());
 const bool saved=CredWriteW(&credential,0)!=FALSE;SecureZeroMemory(token.data(),token.size());return saved;
}

// ── Masuk lewat email (OTP enam digit), di dalam aplikasi ───────────────────
// Aturan formulirnya ada di email_login.h (murni, teruji di Linux); dua fungsi
// di bawah hanya bagian yang menyentuh jaringan dan Credential Manager.
struct OtpResult {
 bool ok=false;          // permintaan diterima server
 bool offline=false;     // tidak sampai ke server sama sekali
 std::string error;      // kode galat Worker ("cooldown", "wrong-otp", …)
 int retryIn=0;          // detik dari `resend_in` / `retry_in`
 Result user;            // hanya terisi pada verifikasi yang berhasil
};
inline OtpResult readOtpError(const Response& response){
 OtpResult out{};
 if(response.status==0){out.offline=true;out.error="offline";return out;}
 if(response.body.is_object()){
  if(response.body.contains("error")&&response.body["error"].is_string())out.error=response.body["error"].get<std::string>();
  for(const char* field:{"resend_in","retry_in"}){
   if(response.body.contains(field)&&response.body[field].is_number_integer()){out.retryIn=std::clamp(response.body[field].get<int>(),0,3600);break;}
  }
 }
 if(out.error.empty())out.error="unknown";
 return out;
}
/** `POST /auth/request-otp` — minta kode sekali pakai ke alamat `email`. */
inline OtpResult sendOtp(const std::string& email,const std::string& body){
 const auto response=requestFull(L"POST",L"/auth/request-otp",body);
 if(response.status==200)return OtpResult{true,false,{},0,{}};
 (void)email;return readOtpError(response);
}
/**
 * `POST /auth/verify-otp` — tukar enam digit jadi sesi.
 *
 * Token hasil verifikasi langsung masuk Credential Manager di sini dan tidak
 * pernah dikembalikan ke lapisan UI; yang naik hanya nama dan alamat.
 */
inline OtpResult verifyOtp(const std::string& body){
 const auto response=requestFull(L"POST",L"/auth/verify-otp",body);
 if(response.status!=200)return readOtpError(response);
 auto user=profile(response.body);
 if(!user.ok||!response.body.contains("token")||!response.body["token"].is_string())
  return OtpResult{false,false,"unknown",0,{}};
 auto token=response.body["token"].get<std::string>();
 if(!storeSession(token,user.email))
  return OtpResult{false,false,"store-failed",0,{}};
 return OtpResult{true,false,{},0,user};
}
}
