; The version comes from the build: iscc /DMyAppVersion=<version> installer.iss
#ifndef MyAppVersion
#define MyAppVersion "1.0.0"
#endif

[Setup]
AppId={{D7B93A8E-4F12-4C2A-9E81-2A6B4D3F5E01}}
AppName=Yabai OCR
AppVersion={#MyAppVersion}
AppPublisher=Yabai Team
DefaultDirName={autopf}\Yabai OCR
DefaultGroupName=Yabai OCR
DisableProgramGroupPage=yes
OutputDir=..\..\build\distributions\inno
OutputBaseFilename=Yabai_OCR_Setup
SetupIconFile=..\..\src\main\resources\ic_scan_windows.ico
UninstallDisplayIcon={app}\Yabai OCR.exe
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Criar um atalho na Área de Trabalho"; GroupDescription: "Atalhos adicionais:"; Flags: unchecked

[Files]
Source: "..\..\build\distributions\main\app\Yabai OCR\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Yabai OCR"; Filename: "{app}\Yabai OCR.exe"
Name: "{autodesktop}\Yabai OCR"; Filename: "{app}\Yabai OCR.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\Yabai OCR.exe"; Description: "Executar o Yabai OCR"; Flags: postinstall nowait skipifsilent
