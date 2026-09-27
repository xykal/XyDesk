#include "control_contract.h"
#include "vendor/qrcodegen/qrcodegen.hpp"
#include <iostream>
#include <cassert>
int main(){
 const auto wide=xydesk::panel_control::deviceLink(L"123456789");const std::string link(wide.begin(),wide.end());
 auto qr=qrcodegen::QrCode::encodeText(link.c_str(),qrcodegen::QrCode::Ecc::MEDIUM);
 assert(qr.getSize()>=21);
 std::cout<<"{\"link\":\""<<link<<"\",\"size\":"<<qr.getSize()<<",\"modules\":[";
 for(int y=0;y<qr.getSize();++y)for(int x=0;x<qr.getSize();++x){if(x||y)std::cout<<',';std::cout<<(qr.getModule(x,y)?1:0);}
 std::cout<<"]}\n";
}
