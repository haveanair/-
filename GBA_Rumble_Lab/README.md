# GBA Rumble Lab

다른 GBA 게임의 진동 패치를 실제 실행 상태에서 검증하기 위한 공용 테스트 환경입니다.

## 포함된 것

- mGBA 0.10.5 기반 GBA-only libretro 코어
  - 빌드 기준 태그: `0.10.5`
  - mGBA 소스 커밋: `26b7884bc25a5933960f3cdcd98bac1ae14d42e2`
  - Linux x86-64, stripped
  - 압축 SHA-256: `ba40fc443bdcf8e6ef12e50c5332a779014a9e44499d00bcb942d39ac6ad57d3`
  - 압축 해제 후 SHA-256: `f8b44979bb6a9c93fcdf37d74e72181c66b8d833074dc48514506a7969e6e7ee`
- `probe/rumble_probe.py`
  - ROM을 mGBA libretro 코어에서 headless 실행
  - 프레임별 rumble callback 기록
  - strong/weak 모터 및 0~65535 강도 기록
  - CSV 입력 스크립트로 버튼 자동 입력
  - 선택적으로 framebuffer CRC 기록
- Linux/Windows 실행 예시

## 빌드된 코어 위치

이 브랜치에 이미 보존되어 있습니다.

`.v-rally-runtime/mgba_libretro.so.xz`

V-Rally 작업용으로 처음 만든 파일이지만 코어 자체는 V-Rally 전용 개조가 전혀 없는 mGBA 0.10.5 GBA-only libretro 코어이므로 다른 GBA ROM에도 그대로 사용합니다.

## Linux 사용

```bash
xz -dk .v-rally-runtime/mgba_libretro.so.xz
python3 GBA_Rumble_Lab/probe/rumble_probe.py \
  --core .v-rally-runtime/mgba_libretro.so \
  --rom GAME.gba \
  --frames 3600 \
  --script GBA_Rumble_Lab/examples/input_script.csv \
  --out test-output
```

결과물:

- `summary.json`: 코어/ROM SHA-256, 실행 프레임 수, FPS, rumble 호출 수
- `rumble.csv`: 프레임, 포트, strong/weak, 강도
- `frame_crc.csv`: `--frame-crc-every N`을 사용했을 때 생성

## Windows 사용

RetroArch의 mGBA 코어를 그대로 사용할 수 있습니다. 보통 `mgba_libretro.dll`을 지정하면 됩니다.

```powershell
python .\GBA_Rumble_Lab\probe\rumble_probe.py `
  --core "C:\RetroArch-Win64\cores\mgba_libretro.dll" `
  --rom "D:\ROM\GAME.gba" `
  --frames 3600 `
  --script .\GBA_Rumble_Lab\examples\input_script.csv `
  --out .\test-output
```

`run_windows.ps1`은 흔한 RetroArch 설치 경로에서 `mgba_libretro.dll`을 자동 탐색합니다.

## 입력 스크립트

CSV 형식:

```csv
start,end,buttons
0,119,"START"
120,239,"A"
240,899,"A+RIGHT"
900,959,"B+LEFT"
```

버튼 이름: `A B L R START SELECT UP DOWN LEFT RIGHT`.

여러 구간이 겹치면 버튼을 합쳐서 누릅니다. 이를 이용해 메뉴 진입, 경기 시작, 가속/조향 등을 자동화할 수 있습니다.

## 진동 판정 기준

`rumble.csv`의 `strength > 0`은 실제 libretro rumble callback이 발생했다는 뜻입니다. 단순히 ROM 내부에 진동 코드가 존재한다는 정적 판정이 아니라, mGBA에서 해당 경로가 실제 실행된 결과입니다.

진동 종료도 `strength=0` 호출로 기록됩니다. 따라서 지속시간, 펄스 밀도, 강도 변화, strong/weak 채널 사용을 프레임 단위로 비교할 수 있습니다.

## 주의

- ROM은 저장소에 넣지 않습니다.
- 이 도구는 진동 출력 검증용이며 실제 모터의 체감 세기는 컨트롤러/프런트엔드에 따라 달라질 수 있습니다.
- GBA 게임 자체의 진행을 자동화하려면 게임별 입력 CSV가 필요합니다.
- 실행 검증에서는 ROM SHA-256을 반드시 결과와 함께 보존하십시오.
