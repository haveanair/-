#!/usr/bin/env python3
import argparse
import csv
import ctypes as C
import hashlib
import json
import zlib
from pathlib import Path

RETRO_DEVICE_JOYPAD = 1
RETRO_ENVIRONMENT_GET_CAN_DUPE = 3
RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY = 9
RETRO_ENVIRONMENT_SET_PIXEL_FORMAT = 10
RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS = 11
RETRO_ENVIRONMENT_GET_VARIABLE = 15
RETRO_ENVIRONMENT_SET_VARIABLES = 16
RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE = 17
RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME = 18
RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE = 23
RETRO_ENVIRONMENT_GET_LOG_INTERFACE = 27
RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY = 31
RETRO_ENVIRONMENT_SET_CONTROLLER_INFO = 35
RETRO_ENVIRONMENT_SET_MEMORY_MAPS = 36
RETRO_ENVIRONMENT_GET_LANGUAGE = 39
RETRO_ENVIRONMENT_GET_INPUT_BITMASKS = 51
RETRO_LANGUAGE_ENGLISH = 0
RETRO_RUMBLE_STRONG = 0
RETRO_RUMBLE_WEAK = 1

JOY_IDS = {
    "B": 0, "Y": 1, "SELECT": 2, "START": 3,
    "UP": 4, "DOWN": 5, "LEFT": 6, "RIGHT": 7,
    "A": 8, "X": 9, "L": 10, "R": 11,
    "L2": 12, "R2": 13, "L3": 14, "R3": 15,
}

ENV_CB = C.CFUNCTYPE(C.c_bool, C.c_uint, C.c_void_p)
VIDEO_CB = C.CFUNCTYPE(None, C.c_void_p, C.c_uint, C.c_uint, C.c_size_t)
AUDIO_CB = C.CFUNCTYPE(None, C.c_int16, C.c_int16)
AUDIO_BATCH_CB = C.CFUNCTYPE(C.c_size_t, C.POINTER(C.c_int16), C.c_size_t)
INPUT_POLL_CB = C.CFUNCTYPE(None)
INPUT_STATE_CB = C.CFUNCTYPE(C.c_int16, C.c_uint, C.c_uint, C.c_uint, C.c_uint)
RUMBLE_CB = C.CFUNCTYPE(C.c_bool, C.c_uint, C.c_uint, C.c_uint16)

class RetroGameInfo(C.Structure):
    _fields_ = [("path", C.c_char_p), ("data", C.c_void_p), ("size", C.c_size_t), ("meta", C.c_char_p)]

class RetroSystemInfo(C.Structure):
    _fields_ = [("library_name", C.c_char_p), ("library_version", C.c_char_p),
                ("valid_extensions", C.c_char_p), ("need_fullpath", C.c_bool), ("block_extract", C.c_bool)]

class RetroGameGeometry(C.Structure):
    _fields_ = [("base_width", C.c_uint), ("base_height", C.c_uint),
                ("max_width", C.c_uint), ("max_height", C.c_uint), ("aspect_ratio", C.c_float)]

class RetroSystemTiming(C.Structure):
    _fields_ = [("fps", C.c_double), ("sample_rate", C.c_double)]

class RetroSystemAVInfo(C.Structure):
    _fields_ = [("geometry", RetroGameGeometry), ("timing", RetroSystemTiming)]

class RetroRumbleInterface(C.Structure):
    _fields_ = [("set_rumble_state", RUMBLE_CB)]

class RetroVariable(C.Structure):
    _fields_ = [("key", C.c_char_p), ("value", C.c_char_p)]

def parse_script(path):
    spans = []
    if not path:
        return spans
    with Path(path).open("r", encoding="utf-8-sig", newline="") as f:
        reader = csv.DictReader(f)
        if not {"start", "end", "buttons"}.issubset(reader.fieldnames or []):
            raise ValueError("input CSV requires columns: start,end,buttons")
        for row in reader:
            start, end = int(row["start"]), int(row["end"])
            if end < start:
                raise ValueError(f"bad span {start}..{end}")
            buttons = set()
            for name in (row.get("buttons") or "").replace("+", " ").replace(";", " ").split():
                key = name.upper()
                if key not in JOY_IDS:
                    raise ValueError(f"unknown button: {name}")
                buttons.add(JOY_IDS[key])
            spans.append((start, end, buttons))
    return spans

class Probe:
    def __init__(self, core_path, rom_path, frames, script, out_dir, frame_crc_every=0):
        self.core_path = Path(core_path)
        self.rom_path = Path(rom_path)
        self.frames = frames
        self.script = script
        self.out_dir = Path(out_dir)
        self.frame_crc_every = frame_crc_every
        self.frame = 0
        self.pixel_format = 0
        self.current_buttons = set()
        self.rumble_events = []
        self.frame_crcs = []
        self.last_video = (0, 0, 0)
        self.out_dir.mkdir(parents=True, exist_ok=True)
        (self.out_dir / "system").mkdir(exist_ok=True)
        (self.out_dir / "save").mkdir(exist_ok=True)
        self.system_dir_buf = C.create_string_buffer(str(self.out_dir / "system").encode() + b"\0")
        self.save_dir_buf = C.create_string_buffer(str(self.out_dir / "save").encode() + b"\0")
        self.cb_env = ENV_CB(self._environment)
        self.cb_video = VIDEO_CB(self._video)
        self.cb_audio = AUDIO_CB(self._audio)
        self.cb_audio_batch = AUDIO_BATCH_CB(self._audio_batch)
        self.cb_input_poll = INPUT_POLL_CB(self._input_poll)
        self.cb_input_state = INPUT_STATE_CB(self._input_state)
        self.cb_rumble = RUMBLE_CB(self._rumble)
        self.rumble_iface = RetroRumbleInterface(self.cb_rumble)
        self.core = C.CDLL(str(self.core_path))
        self._bind()

    def _bind(self):
        c = self.core
        c.retro_set_environment.argtypes = [ENV_CB]
        c.retro_set_video_refresh.argtypes = [VIDEO_CB]
        c.retro_set_audio_sample.argtypes = [AUDIO_CB]
        c.retro_set_audio_sample_batch.argtypes = [AUDIO_BATCH_CB]
        c.retro_set_input_poll.argtypes = [INPUT_POLL_CB]
        c.retro_set_input_state.argtypes = [INPUT_STATE_CB]
        c.retro_init.argtypes = []
        c.retro_deinit.argtypes = []
        c.retro_api_version.restype = C.c_uint
        c.retro_get_system_info.argtypes = [C.POINTER(RetroSystemInfo)]
        c.retro_get_system_av_info.argtypes = [C.POINTER(RetroSystemAVInfo)]
        c.retro_set_controller_port_device.argtypes = [C.c_uint, C.c_uint]
        c.retro_load_game.argtypes = [C.POINTER(RetroGameInfo)]
        c.retro_load_game.restype = C.c_bool
        c.retro_unload_game.argtypes = []
        c.retro_run.argtypes = []

    def _environment(self, cmd, data):
        if cmd == RETRO_ENVIRONMENT_GET_RUMBLE_INTERFACE:
            C.memmove(data, C.byref(self.rumble_iface), C.sizeof(self.rumble_iface))
            return True
        if cmd == RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            self.pixel_format = C.cast(data, C.POINTER(C.c_int))[0]
            return True
        if cmd == RETRO_ENVIRONMENT_GET_CAN_DUPE:
            C.cast(data, C.POINTER(C.c_bool))[0] = True
            return True
        if cmd == RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY:
            C.cast(data, C.POINTER(C.c_char_p))[0] = C.cast(self.system_dir_buf, C.c_char_p)
            return True
        if cmd == RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY:
            C.cast(data, C.POINTER(C.c_char_p))[0] = C.cast(self.save_dir_buf, C.c_char_p)
            return True
        if cmd == RETRO_ENVIRONMENT_GET_LANGUAGE:
            C.cast(data, C.POINTER(C.c_uint))[0] = RETRO_LANGUAGE_ENGLISH
            return True
        if cmd == RETRO_ENVIRONMENT_GET_VARIABLE:
            C.cast(data, C.POINTER(RetroVariable))[0].value = None
            return True
        if cmd == RETRO_ENVIRONMENT_GET_VARIABLE_UPDATE:
            C.cast(data, C.POINTER(C.c_bool))[0] = False
            return True
        if cmd == RETRO_ENVIRONMENT_GET_INPUT_BITMASKS:
            return False
        if cmd in (RETRO_ENVIRONMENT_SET_INPUT_DESCRIPTORS, RETRO_ENVIRONMENT_SET_VARIABLES,
                   RETRO_ENVIRONMENT_SET_CONTROLLER_INFO, RETRO_ENVIRONMENT_SET_MEMORY_MAPS,
                   RETRO_ENVIRONMENT_SET_SUPPORT_NO_GAME):
            return True
        if cmd == RETRO_ENVIRONMENT_GET_LOG_INTERFACE:
            return False
        return False

    def _video(self, data, width, height, pitch):
        self.last_video = (int(width), int(height), int(pitch))
        if data and self.frame_crc_every and self.frame % self.frame_crc_every == 0:
            try:
                raw = C.string_at(data, int(pitch) * int(height))
                self.frame_crcs.append({"frame": self.frame, "width": int(width), "height": int(height),
                                        "pitch": int(pitch), "crc32": f"{zlib.crc32(raw) & 0xffffffff:08x}"})
            except (ValueError, OSError):
                pass

    def _audio(self, left, right):
        pass

    def _audio_batch(self, data, frames):
        return frames

    def _input_poll(self):
        buttons = set()
        for start, end, span_buttons in self.script:
            if start <= self.frame <= end:
                buttons.update(span_buttons)
        self.current_buttons = buttons

    def _input_state(self, port, device, index, ident):
        if port != 0 or device != RETRO_DEVICE_JOYPAD or index != 0:
            return 0
        return 1 if int(ident) in self.current_buttons else 0

    def _rumble(self, port, effect, strength):
        self.rumble_events.append({
            "frame": self.frame, "port": int(port),
            "effect": "strong" if effect == RETRO_RUMBLE_STRONG else "weak",
            "strength": int(strength), "strength_hex": f"0x{int(strength):04X}"})
        return True

    def run(self):
        rom_bytes = self.rom_path.read_bytes()
        rom_sha = hashlib.sha256(rom_bytes).hexdigest()
        rom_buf = C.create_string_buffer(rom_bytes)
        rom_path_b = str(self.rom_path.resolve()).encode("utf-8")
        c = self.core
        c.retro_set_environment(self.cb_env)
        c.retro_set_video_refresh(self.cb_video)
        c.retro_set_audio_sample(self.cb_audio)
        c.retro_set_audio_sample_batch(self.cb_audio_batch)
        c.retro_set_input_poll(self.cb_input_poll)
        c.retro_set_input_state(self.cb_input_state)
        c.retro_init()
        info = RetroSystemInfo()
        c.retro_get_system_info(C.byref(info))
        if c.retro_api_version() != 1:
            c.retro_deinit()
            raise RuntimeError("unsupported libretro API version")
        game = RetroGameInfo(C.c_char_p(rom_path_b), C.cast(rom_buf, C.c_void_p), len(rom_bytes), None)
        if not c.retro_load_game(C.byref(game)):
            c.retro_deinit()
            raise RuntimeError("retro_load_game failed")
        c.retro_set_controller_port_device(0, RETRO_DEVICE_JOYPAD)
        av = RetroSystemAVInfo()
        c.retro_get_system_av_info(C.byref(av))
        try:
            for f in range(self.frames):
                self.frame = f
                c.retro_run()
        finally:
            c.retro_unload_game()
            c.retro_deinit()
        summary = {
            "core": str(self.core_path),
            "library_name": (info.library_name or b"").decode("utf-8", "replace"),
            "library_version": (info.library_version or b"").decode("utf-8", "replace"),
            "rom": str(self.rom_path), "rom_sha256": rom_sha, "frames": self.frames,
            "nominal_fps": av.timing.fps, "sample_rate": av.timing.sample_rate,
            "pixel_format": self.pixel_format,
            "last_video": {"width": self.last_video[0], "height": self.last_video[1], "pitch": self.last_video[2]},
            "rumble_event_count": len(self.rumble_events),
            "rumble_nonzero_count": sum(1 for e in self.rumble_events if e["strength"] > 0),
        }
        (self.out_dir / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
        with (self.out_dir / "rumble.csv").open("w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=["frame", "port", "effect", "strength", "strength_hex"])
            w.writeheader(); w.writerows(self.rumble_events)
        if self.frame_crcs:
            with (self.out_dir / "frame_crc.csv").open("w", newline="", encoding="utf-8") as f:
                w = csv.DictWriter(f, fieldnames=["frame", "width", "height", "pitch", "crc32"])
                w.writeheader(); w.writerows(self.frame_crcs)
        return summary

def main():
    ap = argparse.ArgumentParser(description="Headless libretro GBA rumble probe")
    ap.add_argument("--core", required=True, type=Path)
    ap.add_argument("--rom", required=True, type=Path)
    ap.add_argument("--frames", type=int, default=3600)
    ap.add_argument("--script", type=Path)
    ap.add_argument("--out", type=Path, default=Path("probe-out"))
    ap.add_argument("--frame-crc-every", type=int, default=0)
    args = ap.parse_args()
    if not args.core.is_file(): ap.error(f"core not found: {args.core}")
    if not args.rom.is_file(): ap.error(f"ROM not found: {args.rom}")
    script = parse_script(args.script) if args.script else []
    summary = Probe(args.core, args.rom, args.frames, script, args.out, args.frame_crc_every).run()
    print(json.dumps(summary, indent=2, ensure_ascii=False))

if __name__ == "__main__":
    main()
