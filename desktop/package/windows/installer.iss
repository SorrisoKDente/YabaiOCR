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
; The app lives in the tray: closing its window only hides it, so Restart Manager's polite close
; leaves the process running and the installer cannot replace the jars. It is terminated below,
; in [Code], before the files are written.
CloseApplications=no
RestartApplications=no

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

[Code]
{ The tray app answers the close request by hiding its window, so a request to close it never ends
  the process and the update cannot replace the files. Ending it here is what the «close the running
  application» option is expected to do; the installer starts it again at the end. }
procedure TerminateRunningApp;
var
  ResultCode: Integer;
begin
  Exec(
    ExpandConstant('{sys}\taskkill.exe'),
    '/IM "Yabai OCR.exe" /F',
    '',
    SW_HIDE,
    ewWaitUntilTerminated,
    ResultCode
  );
  { Give the process a moment to release the jars before the copy starts. }
  Sleep(600);
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  TerminateRunningApp;
  Result := '';
end;
