#pragma once
// Only inherited private IPC supplies this capability. Never discover it from files/logs.
#ifndef _WIN32_WINNT
#define _WIN32_WINNT 0x0A00
#endif
#include <windows.h>
#include <winhttp.h>
#include <vector>
#include "control_contract.h"
#pragma comment(lib, "winhttp.lib")
namespace xydesk::panel_control {
struct InternetHandle {
    HINTERNET value = nullptr;
    explicit InternetHandle(HINTERNET h) : value(h) {}
    ~InternetHandle(){if(value)WinHttpCloseHandle(value);}
    InternetHandle(const InternetHandle&)=delete;
    InternetHandle& operator=(const InternetHandle&)=delete;
};
// Synchronous worker-only function; caller keeps the UI responsive with std::async.
inline std::string action(const Endpoint& endpoint, std::string body) {
    InternetHandle session(WinHttpOpen(L"XyDesk native panel", WINHTTP_ACCESS_TYPE_NO_PROXY, WINHTTP_NO_PROXY_NAME, WINHTTP_NO_PROXY_BYPASS, 0));
    if(!session.value)return {};
    WinHttpSetTimeouts(session.value, 1500, 1500, 2000, 2000);
    InternetHandle connection(WinHttpConnect(session.value,L"127.0.0.1",endpoint.port,0));
    if(!connection.value)return {};
    InternetHandle request(WinHttpOpenRequest(connection.value,L"POST",L"/action",nullptr,WINHTTP_NO_REFERER,WINHTTP_DEFAULT_ACCEPT_TYPES,0));
    if(!request.value)return {};
    DWORD redirects=WINHTTP_OPTION_REDIRECT_POLICY_NEVER;
    if(!WinHttpSetOption(request.value,WINHTTP_OPTION_REDIRECT_POLICY,&redirects,sizeof(redirects)))return {};
    const std::wstring headers=L"Content-Type: application/json\r\nx-xydesk-token: "+std::wstring(endpoint.token.begin(),endpoint.token.end())+L"\r\n";
    const BOOL sent=WinHttpSendRequest(request.value,headers.c_str(),static_cast<DWORD>(headers.size()),body.data(),static_cast<DWORD>(body.size()),static_cast<DWORD>(body.size()),0);
    if(!body.empty())SecureZeroMemory(body.data(),body.size());
    if(!sent || !WinHttpReceiveResponse(request.value,nullptr))return {};
    DWORD code=0, size=sizeof(code);
    if(!WinHttpQueryHeaders(request.value,WINHTTP_QUERY_STATUS_CODE|WINHTTP_QUERY_FLAG_NUMBER,WINHTTP_HEADER_NAME_BY_INDEX,&code,&size,WINHTTP_NO_HEADER_INDEX)||code!=200)return {};
    std::string response; const ULONGLONG deadline=GetTickCount64()+5000;
    while(GetTickCount64()<deadline){
        char buffer[1024];DWORD count=0;
        if(!WinHttpReadData(request.value,buffer,sizeof(buffer),&count))return {};
        if(!count)return response;
        if(response.size()+count>8192)return {};
        response.append(buffer,count);
    }
    return {};
}
class Channel {
    HANDLE reader=nullptr; std::string frame; ULONGLONG deadline=0; unsigned child=0;
public:
    std::optional<Endpoint> endpoint;
    void reset(){if(reader)CloseHandle(reader);reader=nullptr;if(!frame.empty())SecureZeroMemory(frame.data(),frame.size());frame.clear();if(endpoint&&!endpoint->token.empty())SecureZeroMemory(endpoint->token.data(),endpoint->token.size());endpoint.reset();child=0;}
    ~Channel(){reset();}
    bool launch(const std::wstring& command,const std::wstring& directory,HANDLE log,PROCESS_INFORMATION& pi){
        reset();SECURITY_ATTRIBUTES sa{sizeof(sa),nullptr,TRUE};HANDLE writer=nullptr;
        if(!CreatePipe(&reader,&writer,&sa,4096))return false;
        if(!SetHandleInformation(reader,HANDLE_FLAG_INHERIT,0)){CloseHandle(writer);reset();return false;}
        HANDLE input=CreateFileW(L"NUL",GENERIC_READ,FILE_SHARE_READ|FILE_SHARE_WRITE,&sa,OPEN_EXISTING,0,nullptr);
        if(input==INVALID_HANDLE_VALUE){CloseHandle(writer);reset();return false;}
        SIZE_T bytes=0;InitializeProcThreadAttributeList(nullptr,1,0,&bytes);
        std::vector<unsigned char> storage(bytes);
        auto attributes=reinterpret_cast<LPPROC_THREAD_ATTRIBUTE_LIST>(storage.data());
        bool initialized=InitializeProcThreadAttributeList(attributes,1,0,&bytes)!=FALSE;
        HANDLE inherited[]={log,writer,input};
        BOOL started=FALSE;
        if(initialized&&UpdateProcThreadAttribute(attributes,0,PROC_THREAD_ATTRIBUTE_HANDLE_LIST,inherited,sizeof(inherited),nullptr,nullptr)){
            STARTUPINFOEXW si{};si.StartupInfo.cb=sizeof(si);si.lpAttributeList=attributes;
            si.StartupInfo.dwFlags=STARTF_USESTDHANDLES;si.StartupInfo.hStdInput=input;si.StartupInfo.hStdOutput=log;si.StartupInfo.hStdError=log;
            auto full=command+L" --control-info-handle "+std::to_wstring(reinterpret_cast<std::uintptr_t>(writer));
            std::vector<wchar_t> line(full.begin(),full.end());line.push_back(0);
            // Suspend until the caller binds the child to its kill-on-close job.
            started=CreateProcessW(nullptr,line.data(),nullptr,nullptr,TRUE,CREATE_SUSPENDED|CREATE_NO_WINDOW|EXTENDED_STARTUPINFO_PRESENT|CREATE_UNICODE_ENVIRONMENT,nullptr,directory.c_str(),&si.StartupInfo,&pi);
        }
        const DWORD launchError=started?ERROR_SUCCESS:GetLastError();
        if(initialized)DeleteProcThreadAttributeList(attributes);
        CloseHandle(writer);CloseHandle(input);
        if(!started){reset();SetLastError(launchError);return false;}
        child=pi.dwProcessId;deadline=GetTickCount64()+10000;return true;
    }
    void poll(HANDLE process){
        if(!reader)return;
        DWORD available=0,exit=0;
        if(!process||GetProcessId(process)!=child||!GetExitCodeProcess(process,&exit)||exit!=STILL_ACTIVE||GetTickCount64()>deadline||!PeekNamedPipe(reader,nullptr,0,nullptr,&available,nullptr)){reset();return;}
        if(!available)return;
        if(frame.size()+available>1024){reset();return;}
        char buffer[1024];DWORD count=0;
        if(!ReadFile(reader,buffer,available,&count,nullptr)){reset();return;}
        frame.append(buffer,count);SecureZeroMemory(buffer,sizeof(buffer));
        if(frame.find('\n')==std::string::npos)return;
        endpoint=bootstrap(frame,child);CloseHandle(reader);reader=nullptr;
        SecureZeroMemory(frame.data(),frame.size());frame.clear();
    }
};
}
