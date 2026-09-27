#define NOMINMAX
#include "control_client.h"
#include <cassert>
#include <fstream>
#include <filesystem>
#include <iostream>
using namespace xydesk::panel_control;
bool success(const std::string& response){auto o=xydesk::engine_json::parse(response);return o&&o->count("ok")&&std::holds_alternative<bool>(o->at("ok"))&&std::get<bool>(o->at("ok"));}
int wmain(int argc,wchar_t** argv){
    assert(argc==2); // runner MUST supply an isolated XYDESK_HOME
    wchar_t home[32768]{};assert(GetEnvironmentVariableW(L"XYDESK_HOME",home,32768)>0);
    SECURITY_ATTRIBUTES sa{sizeof(sa),nullptr,TRUE};
    HANDLE log=CreateFileW(L"NUL",GENERIC_WRITE,FILE_SHARE_READ|FILE_SHARE_WRITE,&sa,OPEN_EXISTING,0,nullptr);assert(log!=INVALID_HANDLE_VALUE);
    HANDLE job=CreateJobObjectW(nullptr,nullptr);assert(job);
    JOBOBJECT_EXTENDED_LIMIT_INFORMATION info{};info.BasicLimitInformation.LimitFlags=JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;assert(SetInformationJobObject(job,JobObjectExtendedLimitInformation,&info,sizeof(info)));
    Channel channel;PROCESS_INFORMATION process{};
    const auto directory=std::filesystem::path(argv[1]).parent_path().wstring();
    const std::wstring command=L"\""+std::wstring(argv[1])+L"\" --url ws://127.0.0.1:9/ws --token local-validation-only";
    assert(channel.launch(command,directory,log,process));assert(AssignProcessToJobObject(job,process.hProcess));assert(ResumeThread(process.hThread)!=static_cast<DWORD>(-1));CloseHandle(process.hThread);
    const ULONGLONG deadline=GetTickCount64()+12000;
    while(!channel.endpoint&&GetTickCount64()<deadline){channel.poll(process.hProcess);Sleep(10);}assert(channel.endpoint);
    const auto endpoint=*channel.endpoint;
    auto wrong=endpoint;wrong.token.assign(32,'0');assert(action(wrong,"{\"action\":\"video-bitrate\",\"bitrate_mbps\":1}").empty());
    assert(success(action(endpoint,"{\"action\":\"video-bitrate\",\"bitrate_mbps\":1}")));
    assert(!success(action(endpoint,"{\"action\":\"video-bitrate\",\"bitrate_mbps\":999}")));
    assert(!success(action(endpoint,"{\"action\":\"set-password\",\"password\":\"abc\"}")));
    const std::string password="Fixture-A\"B\\2026";
    const auto response=action(endpoint,"{\"action\":\"set-password\",\"password\":"+quoteJson(password)+"}");assert(success(response));
    const auto parsed=xydesk::engine_json::parse(response);assert(std::get<std::string>(parsed->at("password"))==password);
    std::ifstream file(std::filesystem::path(home)/"password",std::ios::binary);std::string stored((std::istreambuf_iterator<char>(file)),{});assert(stored==password);file.close();
    assert(success(action(endpoint,"{\"action\":\"new-password\"}")));
    channel.reset();assert(!channel.endpoint);TerminateJobObject(job,0);WaitForSingleObject(process.hProcess,5000);CloseHandle(process.hProcess);CloseHandle(job);CloseHandle(log);
    std::cout<<"Native launcher, private bootstrap, WinHTTP auth, bitrate, password persistence: passed\n";
}
