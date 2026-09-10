"""自制感知过渡声。--write 生成两个衍生 OGG；默认只校验，不读取外部素材。"""
import argparse
import hashlib
import json
import pathlib
import shutil
import subprocess
import numpy as np

RATE = 48000
DURATION = 0.65
SEED = 20260911
ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/magicaland_gameplay"


def synthesize():
    count = round(RATE * DURATION)
    time = np.arange(count, dtype=np.float64) / RATE
    progress = np.arange(count, dtype=np.float64) / (count - 1)
    log_ratio = np.log(3.0 / 16.0)
    cycles = 16 * DURATION / log_ratio * np.expm1(log_ratio * progress)
    pulse = 0.28 + 0.72 * (0.5 + 0.5 * np.cos(2 * np.pi * cycles)) ** 2
    phase = 2 * np.pi * (220 * time - 27.5 * time ** 2 / DURATION)
    tone = np.sin(phase) + 0.24 * np.sin(1.99 * phase) + 0.09 * np.sin(3.02 * phase)
    grain = np.random.default_rng(SEED).normal(size=count)
    kernel = np.exp(-0.5 * (np.arange(-60, 61) / 18) ** 2)
    grain = np.convolve(grain, kernel / kernel.sum(), mode="same")
    grain /= max(np.max(np.abs(grain)), 1e-12)
    envelope = np.sin(np.pi * progress) ** 1.8
    enter = (tone * pulse + grain * 0.065) * envelope
    enter *= 10 ** (-14 / 20) / max(np.max(np.abs(enter)), 1e-12)
    enter[0] = enter[-1] = 0
    enter = enter.astype("<f4")
    return {"enter": enter, "exit": enter[::-1].copy()}


def db(value):
    return float(20 * np.log10(max(float(value), 1e-12)))


def run(command, **kwargs):
    return subprocess.run(command, check=True, capture_output=True, **kwargs).stdout


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--ffmpeg", default=shutil.which("ffmpeg") or "C:/Program Files/Virtual Desktop Streamer/ffmpeg.exe")
    parser.add_argument("--report", type=pathlib.Path)
    options = parser.parse_args()
    ffmpeg = str(options.ffmpeg)
    samples = synthesize()
    assert np.array_equal(samples["exit"], samples["enter"][::-1]), "Exit must reverse the original PCM exactly"
    events = json.loads((ASSETS / "sounds.json").read_text(encoding="utf8"))
    metadata = {"origin": "Original deterministic synthesis; no third-party recording", "seconds": DURATION,
                "sample_rate": RATE, "seed": SEED, "enter_pulse_hz": [16, 3], "exit_pulse_hz": [3, 16],
                "runtime_volume": 0.5, "sounds": {}}
    for name, pcm in samples.items():
        target = ASSETS / "sounds/sense" / (name + ".ogg")
        assert events["sense." + name]["sounds"] == [{"name": "magicaland_gameplay:sense/" + name, "preload": True}]
        if options.write:
            target.parent.mkdir(parents=True, exist_ok=True)
            run([ffmpeg, "-v", "error", "-y", "-f", "f32le", "-ar", str(RATE), "-ac", "1",
                 "-i", "pipe:0", "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact",
                 "-c:a", "libvorbis", "-q:a", "5", str(target)], input=pcm.tobytes())
        decoded = np.frombuffer(run([ffmpeg, "-v", "error", "-i", str(target), "-f", "f32le",
                                     "-ar", str(RATE), "-ac", "1", "pipe:1"]), dtype="<f4")
        assert abs(len(decoded) - len(pcm)) <= 128
        assert np.isfinite(decoded).all()
        assert db(np.max(np.abs(decoded))) < -12.5, "Quiet encoding headroom"
        assert max(abs(float(decoded[0])), abs(float(decoded[-1]))) < 0.001, "No sharp boundary click"
        overlap = min(len(decoded), len(pcm))
        correlation = float(np.corrcoef(decoded[:overlap], pcm[:overlap])[0, 1])
        assert correlation > 0.97, "Encoded asset does not match deterministic source"
        power = np.abs(np.fft.rfft(decoded)) ** 2
        frequency = np.fft.rfftfreq(len(decoded), 1 / RATE)
        high_fraction = float(power[frequency > 2500].sum() / power.sum())
        assert high_fraction < 0.001, "Keep the transition soft, not piercing"
        metadata["sounds"][name] = {"bytes": target.stat().st_size,
            "sha256": hashlib.sha256(target.read_bytes()).hexdigest(),
            "pcm_sha256": hashlib.sha256(pcm.tobytes()).hexdigest(),
            "peak_dbfs": db(np.max(np.abs(decoded))), "rms_dbfs": db(np.sqrt(np.mean(decoded.astype(float) ** 2))),
            "decoded_correlation": correlation, "energy_above_2500hz": high_fraction}
    if options.report:
        options.report.parent.mkdir(parents=True, exist_ok=True)
        options.report.write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf8")
    print(json.dumps(metadata, ensure_ascii=False, indent=2))
    print("PASS: original quiet mono Vorbis pair; exact reversed source PCM; decoded assets and gentle spectrum verified")


if __name__ == "__main__":
    main()
