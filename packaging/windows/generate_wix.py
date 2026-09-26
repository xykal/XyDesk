#!/usr/bin/env python3
"""Generate a deterministic WiX v4 manifest for the verified Windows bundle."""

from __future__ import annotations

import argparse
import hashlib
import html
from pathlib import Path


def ident(prefix: str, value: str) -> str:
    digest = hashlib.sha1(value.encode("utf-8")).hexdigest()[:16]
    return f"{prefix}_{digest}"


def esc(value: str) -> str:
    return html.escape(value, quote=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-dir", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--version", required=True)
    args = parser.parse_args()

    source = args.source_dir.resolve()
    files = sorted(path.relative_to(source) for path in source.rglob("*") if path.is_file())
    if not files:
        raise SystemExit("bundle kosong")
    if not (source / "XyDesk.exe").is_file():
        raise SystemExit("XyDesk.exe tidak ditemukan")
    if not (source / "xydesk-host.exe").is_file():
        raise SystemExit("xydesk-host.exe tidak ditemukan")
    if not (source / "LICENSE-XyDesk.txt").is_file():
        raise SystemExit("LICENSE-XyDesk.txt tidak ditemukan")
    if not (source / "LICENSE-XyDesk-English.rtf").is_file():
        raise SystemExit("LICENSE-XyDesk-English.rtf tidak ditemukan")

    directories: set[Path] = set()
    for path in files:
        directories.update(parent for parent in path.parents if parent != Path("."))
    directories = sorted(directories, key=lambda item: (len(item.parts), item.as_posix()))
    directory_ids = {Path("."): "INSTALLFOLDER"}
    for directory in directories:
        directory_ids[directory] = ident("dir", directory.as_posix())

    children: dict[Path, list[Path]] = {}
    for directory in directories:
        children.setdefault(directory.parent, []).append(directory)
    for values in children.values():
        values.sort(key=lambda item: item.as_posix())

    directory_xml: list[str] = []

    def render_directories(parent: Path, level: int) -> None:
        for directory in children.get(parent, []):
            padding = "  " * level
            directory_xml.append(
                f'{padding}<Directory Id="{directory_ids[directory]}" '
                f'Name="{esc(directory.name)}">'
            )
            render_directories(directory, level + 1)
            directory_xml.append(f'{padding}</Directory>')

    render_directories(Path("."), 3)

    component_xml: list[str] = []
    for path in files:
        relative = path.as_posix()
        component_id = ident("cmp", relative)
        directory_id = directory_ids[path.parent]
        file_id = ident("fil", relative)
        component_xml.append(
            "      <Component "
            f'Id="{component_id}" Directory="{directory_id}" Guid="*">\n'
            f'        <File Id="{file_id}" Source="$(var.SourceDir)\\{esc(relative.replace("/", "\\"))}" KeyPath="yes" />\n'
            "      </Component>"
        )

    # A registry-key component owns the Start Menu shortcut without duplicating
    # the executable into a second MSI component.
    component_xml.append(
        "      <Component Id=\"cmp_StartMenu\" Directory=\"ApplicationProgramsFolder\" Guid=\"*\">\n"
        "        <Shortcut Id=\"StartMenuShortcut\" Name=\"XyDesk Control Panel\"\n"
        "                  Target=\"[INSTALLFOLDER]XyDesk.exe\" WorkingDirectory=\"INSTALLFOLDER\" />\n"
        "        <RemoveFolder Id=\"RemoveStartMenuFolder\" On=\"uninstall\" />\n"
        "        <RegistryValue Root=\"HKCU\" Key=\"Software\\XyDesk\"\n"
        "                       Name=\"installed\" Type=\"integer\" Value=\"1\" KeyPath=\"yes\" />\n"
        "      </Component>"
    )

    # Startup host per pemakai (HKCU Run): tiap login interaktif memulai
    # instance host; kepemimpinan lintas sesi menentukan leader. Nilai
    # registry ditulis komponen ini dan ikut terhapus saat uninstall.
    component_xml.append(
        "      <Component Id=\"cmp_AutoStart\" Directory=\"INSTALLFOLDER\" Guid=\"*\">\n"
        "        <RegistryValue Root=\"HKCU\" Key=\"Software\\Microsoft\\Windows\\CurrentVersion\\Run\"\n"
        "                       Name=\"XyDeskHost\" Type=\"string\"\n"
        "                       Value=\"&quot;[INSTALLFOLDER]xydesk-host.exe&quot; --managed-auth --autostart\" KeyPath=\"yes\" />\n"
        "      </Component>"
    )

    output = f'''<?xml version="1.0" encoding="UTF-8"?>
<Wix xmlns="http://wixtoolset.org/schemas/v4/wxs"
     xmlns:ui="http://wixtoolset.org/schemas/v4/wxs/ui">
  <Package Name="XyDesk" Manufacturer="XySpace Tech" Version="{esc(args.version)}"
           UpgradeCode="9F3DEB68-65B5-48C9-A92D-5A3E7B2CB304"
           Scope="perUser" Compressed="yes">
    <SummaryInformation Description="XyDesk native Windows control panel and Rust engine"
                        Manufacturer="XySpace Tech" />
    <MajorUpgrade DowngradeErrorMessage="A newer version of XyDesk is already installed." />
    <MediaTemplate EmbedCab="yes" />
    <ui:WixUI Id="WixUI_InstallDir" InstallDirectory="INSTALLFOLDER" />
    <WixVariable Id="WixUILicenseRtf" Value="$(var.SourceDir)\\LICENSE-XyDesk-English.rtf" />
    <WixVariable Id="WixUIBannerBmp" Value="packaging/windows/wix-banner.bmp" />
    <WixVariable Id="WixUIDialogBmp" Value="packaging/windows/wix-dialog.bmp" />
    <StandardDirectory Id="LocalAppDataFolder">
      <Directory Id="INSTALLFOLDER" Name="XyDesk">
{chr(10).join(directory_xml)}
      </Directory>
    </StandardDirectory>
    <StandardDirectory Id="ProgramMenuFolder">
      <Directory Id="ApplicationProgramsFolder" Name="XyDesk" />
    </StandardDirectory>
    <Feature Id="MainFeature" Title="XyDesk" Level="1">
      <ComponentGroupRef Id="ProductComponents" />
    </Feature>
  </Package>
  <Fragment>
    <ComponentGroup Id="ProductComponents">
{chr(10).join(component_xml)}
    </ComponentGroup>
  </Fragment>
</Wix>
'''
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(output, encoding="utf-8")
    print(f"generated {args.output} with {len(files)} files")


if __name__ == "__main__":
    main()
