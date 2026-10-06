#pragma once
// Read-only UI projection of the existing authenticated /status API.
// Never expose the status response wholesale: it also contains pairing secrets.
#include "account_auth.h"
#include "control_client.h"
#include "vendor/json/json.hpp"
namespace xydesk::session_view {
struct Snapshot {
 bool known=false,active=false;std::wstring name,platform,id,state;unsigned long long seconds=0;
 // Tawaran berkas masuk yang menunggu jawaban pemilik PC (`pendingFile`).
 // `pendingId==0` berarti tidak ada yang menunggu: host memakai id transfer
 // bukan-nol, dan 0 dipakai protokol sebagai beacon "siap".
 bool pendingRisky=false;unsigned long pendingId=0;unsigned long long pendingSize=0,pendingSeconds=0;std::wstring pendingName;
 // Kebijakan berkas masuk ("ask"/"always"/"never") dan jumlah perangkat yang
 // pernah diingat. Kosong = host lama yang belum melaporkannya.
 std::wstring filePolicy;unsigned long trustedDevices=0;
};
inline std::wstring text(const nlohmann::json& object,const char* field){
 auto it=object.find(field);if(it==object.end()||!it->is_string())return {};
 const auto value=it->get<std::string>();if(value.size()>256)return {};
 return xydesk::account::wide(value);
}
inline bool redmiNote12(const std::wstring& name){
 const auto end=name.find(L" · ");auto model=name.substr(0,end);
 return model==L"Redmi Note 12"||model==L"23021RAAEG";
}
inline Snapshot parse(const std::string& raw){
 auto data=nlohmann::json::parse(raw,nullptr,false);if(!data.is_object()||!data.contains("session"))return {};
 Snapshot result;result.known=true;result.state=text(data,"state");
 result.filePolicy=text(data,"filePolicy");
 if(data.contains("trustedFileDevices")&&data["trustedFileDevices"].is_number_unsigned())result.trustedDevices=data["trustedFileDevices"].get<unsigned long>();
 if(auto pending=data.find("pendingFile");pending!=data.end()&&pending->is_object()){
  const auto& offer=*pending;
  if(offer.contains("id")&&offer["id"].is_number_unsigned()){
   result.pendingId=offer["id"].get<unsigned long>();
   result.pendingName=text(offer,"name");
   if(offer.contains("size")&&offer["size"].is_number_unsigned())result.pendingSize=offer["size"].get<unsigned long long>();
   if(offer.contains("secondsLeft")&&offer["secondsLeft"].is_number_unsigned())result.pendingSeconds=offer["secondsLeft"].get<unsigned long long>();
   result.pendingRisky=offer.contains("risky")&&offer["risky"].is_boolean()&&offer["risky"].get<bool>();
  }
 }
 if(data["session"].is_null())return result;
 if(!data["session"].is_object())return {};
 const auto& session=data["session"];result.id=text(session,"clientId");if(result.id.empty())return {};
 result.active=true;result.name=text(session,"clientName");result.platform=text(session,"clientPlatform");
 if(session.contains("durationMs")&&session["durationMs"].is_number_unsigned())result.seconds=session["durationMs"].get<unsigned long long>()/1000;
 return result;
}
inline Snapshot read(const xydesk::panel_control::Endpoint& endpoint){
 using xydesk::panel_control::InternetHandle;
 InternetHandle session(WinHttpOpen(L"XyDesk session view",WINHTTP_ACCESS_TYPE_NO_PROXY,WINHTTP_NO_PROXY_NAME,WINHTTP_NO_PROXY_BYPASS,0));if(!session.value)return {};
 WinHttpSetTimeouts(session.value,1500,1500,2000,2000);
 InternetHandle connection(WinHttpConnect(session.value,L"127.0.0.1",endpoint.port,0));if(!connection.value)return {};
 InternetHandle request(WinHttpOpenRequest(connection.value,L"GET",L"/status",nullptr,WINHTTP_NO_REFERER,WINHTTP_DEFAULT_ACCEPT_TYPES,0));if(!request.value)return {};
 DWORD redirects=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;if(!WinHttpSetOption(request.value,WINHTTP_OPTION_REDIRECT_POLICY,&redirects,sizeof(redirects)))return {};
 const std::wstring headers=L"x-xydesk-token: "+std::wstring(endpoint.token.begin(),endpoint.token.end())+L"\r\n";
 if(!WinHttpSendRequest(request.value,headers.c_str(),static_cast<DWORD>(headers.size()),nullptr,0,0,0)||!WinHttpReceiveResponse(request.value,nullptr))return {};
 DWORD code=0,size=sizeof(code);if(!WinHttpQueryHeaders(request.value,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&code,&size,WINHTTP_NO_HEADER_INDEX)||code!=200)return {};
 std::string raw;const auto deadline=GetTickCount64()+5000;Snapshot result;
 while(GetTickCount64()<deadline){char buffer[2048];DWORD count=0;if(!WinHttpReadData(request.value,buffer,sizeof(buffer),&count))break;if(!count){result=parse(raw);break;}if(raw.size()+count>65536)break;raw.append(buffer,count);SecureZeroMemory(buffer,sizeof(buffer));}
 SecureZeroMemory(raw.data(),raw.size());return result;
}
}
