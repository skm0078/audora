# S1 desktop pre-run in CI (GitHub Actions ubuntu-latest, 2 threads)

From the s1-voices workflow at commit 20eb6e7. "Hard" CER is not meaningful (ASR writes numbers inconsistently). Raw data: desktop-ci.json.

| Voice | Lang | Disk MB | Load s | First render s | RTF mean | CER prose+formal | CER hard | Note |
|---|---|---|---|---|---|---|---|---|
| piper-en-ljspeech | en | 82 | 1.25 | 0.20 | 0.061 | 0.0% | 595.1% |  |
| piper-en-cori | en | 82 | 1.17 | 0.20 | 0.062 | 0.6% | 18.6% |  |
| kitten-en-nano | en | 46 | 0.48 | 1.61 | 0.320 | 0.0% | 14.7% |  |
| kokoro-en-heart | en | 190 | 1.01 | 4.12 | 1.191 | 0.0% | 13.7% |  |
| supertonic-en | en | 145 | 0.48 | 0.73 | 0.152 | 0.0% | 21.6% |  |
| piper-hi-priyamvada | hi | 81 | 0.73 | 0.25 | 0.059 | 8.5% | 37.3% |  |
| piper-hi-pratham | hi | 81 | 0.74 | 0.23 | 0.062 | 4.9% | 40.1% |  |
| supertonic-hi | hi | 145 | 0.49 | 0.84 | 0.149 | 2.4% | 25.4% |  |
| kokoro-hi-alpha | hi | 190 | 0.70 | 5.74 | 1.146 | 6.1% | 40.8% |  |
| piper-bn-google | bn | 95 | 0.80 | 0.28 | 0.068 | 13.9% | 45.6% |  |
| mms-bn | bn | 114 | 1.21 | 1.44 | 0.369 | 9.7% | 30.9% |  |
